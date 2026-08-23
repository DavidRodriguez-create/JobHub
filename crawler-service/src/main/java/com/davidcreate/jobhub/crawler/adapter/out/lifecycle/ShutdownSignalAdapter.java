package com.davidcreate.jobhub.crawler.adapter.out.lifecycle;

import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownFlag;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownSignal;
import com.davidcreate.jobhub.crawler.domain.port.out.TriggerRequestQueue;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * One shared shutdown signal (ADR 0032, story #398): flips a volatile flag when Quarkus's
 * {@code ShutdownEvent} fires (compose down / SIGTERM) so the three schedulers and the
 * domain batch loops can stop starting new work before the EntityManagerFactory closes.
 *
 * <p>Flipping the flag alone is necessary but not sufficient: a crawl/enrichment batch
 * already executing on a worker thread only checks the flag at the next item boundary, and
 * Quarkus tears the CDI container (and the EntityManagerFactory) down right after all
 * {@code ShutdownEvent} observers return. This observer therefore <b>blocks</b> here, the
 * correct place per Quarkus's shutdown lifecycle, until every in-flight batch has actually
 * exited its loop (tracked explicitly via {@link #workStarted()}/{@link #workFinished()}, not
 * a fixed sleep), up to {@code crawler.shutdown.drain-timeout}. If that timeout expires (the
 * normal case for a slow outbound HTTP/model call, not an edge case), the still-running
 * worker thread(s) are interrupted so their loop unwinds at the next interruptible point
 * instead of running to completion against a closing EntityManagerFactory; whatever they
 * were driving in {@code trigger_request} is marked interrupted right here, while the
 * datasource is still alive, instead of waiting for the next process start's reaper. Any
 * exception the interrupted work itself still raises is swallowed quietly by the batch loops
 * and the trigger scheduler once the shutdown signal is up (see {@code CrawlerService},
 * {@code EnrichmentService}, {@code TriggerRequestScheduler}), so it never reaches Quarkus's
 * uncaught-exception handler.
 */
@ApplicationScoped
public class ShutdownSignalAdapter implements ShutdownSignal {

    private static final Logger LOG = Logger.getLogger(ShutdownSignalAdapter.class);
    static final String SHUTDOWN_REASON = "Interrupted by shutdown";

    private final TriggerRequestQueue triggerRequestQueue;

    @ConfigProperty(name = "crawler.shutdown.drain-timeout", defaultValue = "PT25S")
    Duration drainTimeout;

    private final Object drainLock = new Object();
    private final Set<Thread> inFlightThreads = new HashSet<>();

    public ShutdownSignalAdapter(TriggerRequestQueue triggerRequestQueue) {
        this.triggerRequestQueue = triggerRequestQueue;
    }

    void onShutdown(@Observes ShutdownEvent event) {
        shutdown();
    }

    /**
     * The actual drain-and-reap sequence; {@link #onShutdown} just delegates to it (same split
     * as {@code TriggerRequestReaper#onStart}/{@code reapNonTerminal}), so it is directly
     * unit-testable without reaching for a real {@code ShutdownEvent}.
     */
    public void shutdown() {
        LOG.info("Shutdown signal raised: schedulers and batch loops will stop at the next item boundary");
        // Plain static write (ShutdownFlag), not just the instance field below: readable from
        // any thread even after the CDI container that hosts this bean is gone (4th pass).
        ShutdownFlag.raise();
        drain();
        // D2 (story #398): mark whatever is still non-terminal (running, or the rarer queued/
        // cancel_requested) interrupted right now, while the datasource is still alive. A clean
        // drain already left nothing non-terminal, so this is a no-op in the common case, and
        // the on-the-way-down safety net when the drain timed out.
        triggerRequestQueue.reapNonTerminal(SHUTDOWN_REASON);
    }

    private void drain() {
        long deadlineNanos = System.nanoTime() + drainTimeout.toNanos();
        synchronized (drainLock) {
            while (!inFlightThreads.isEmpty()) {
                long remainingMillis = (deadlineNanos - System.nanoTime()) / 1_000_000L;
                if (remainingMillis <= 0) {
                    LOG.warnf("Drain timeout (%s) expired with %d in-flight run(s) still active; "
                            + "interrupting them and marking them interrupted now, leaving anything "
                            + "else for the startup reaper",
                            drainTimeout, inFlightThreads.size());
                    // Make the abandoned work stop sooner: interrupt so the loop unwinds at the
                    // next interruptible point rather than running to completion underneath a
                    // closing EntityManagerFactory. The resulting exception is expected and is
                    // swallowed quietly downstream once the shutdown signal is up.
                    for (Thread thread : inFlightThreads) {
                        thread.interrupt();
                    }
                    return;
                }
                try {
                    drainLock.wait(remainingMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warn("Drain wait interrupted; proceeding with shutdown");
                    return;
                }
            }
        }
        LOG.info("Drain complete: no in-flight crawl/enrichment work remaining");
    }

    @Override
    public boolean isShuttingDown() {
        return ShutdownFlag.isRaised();
    }

    @Override
    public void workStarted() {
        synchronized (drainLock) {
            inFlightThreads.add(Thread.currentThread());
        }
    }

    @Override
    public void workFinished() {
        synchronized (drainLock) {
            inFlightThreads.remove(Thread.currentThread());
            if (inFlightThreads.isEmpty()) {
                drainLock.notifyAll();
            }
        }
    }
}
