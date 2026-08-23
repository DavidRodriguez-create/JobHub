package com.davidcreate.jobhub.crawler.adapter.in.scheduler;

import com.davidcreate.jobhub.crawler.domain.model.TriggerRequest;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownFlag;
import com.davidcreate.jobhub.crawler.domain.port.out.ShutdownSignal;
import com.davidcreate.jobhub.crawler.domain.port.out.TriggerRequestQueue;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Story #398 (ADR 0032, D2): a restart between {@code markRunning} and {@code markDone} would
 * otherwise strand a {@code trigger_request} row {@code running} forever, permanently blocking
 * {@code TriggerRequestScheduler} from processing ENRICHMENT (it skips while a CRAWL is
 * running). Two halves:
 * <ul>
 *   <li>{@link #reapNonTerminal()} (startup guarantee): every non-terminal row is interrupted
 *       once, unconditionally, when this instance boots.</li>
 *   <li>{@link #sweepStale(OffsetDateTime)} (live sweep, best effort): a {@code running} row
 *       older than {@code crawler.trigger.stale-after} is interrupted without waiting for a
 *       restart, covering a wedged-but-still-alive process.</li>
 * </ul>
 */
@ApplicationScoped
public class TriggerRequestReaper {

    private static final Logger LOG = Logger.getLogger(TriggerRequestReaper.class);
    static final String SHUTDOWN_REASON = "Interrupted by shutdown";

    private final TriggerRequestQueue triggerRequestQueue;
    private final ShutdownSignal shutdownSignal;

    @ConfigProperty(name = "crawler.trigger.stale-after", defaultValue = "PT2H")
    Duration staleAfter;

    public TriggerRequestReaper(TriggerRequestQueue triggerRequestQueue, ShutdownSignal shutdownSignal) {
        this.triggerRequestQueue = triggerRequestQueue;
        this.shutdownSignal = shutdownSignal;
    }

    void onStart(@Observes StartupEvent event) {
        reapNonTerminal();
    }

    public void reapNonTerminal() {
        triggerRequestQueue.reapNonTerminal(SHUTDOWN_REASON);
    }

    @Scheduled(every = "${crawler.trigger.reaper-sweep-cron:10s}")
    public void sweep() {
        // 4th pass (D1, story #398): outer boundary -- nothing below may let a Throwable
        // escape once shutdown has begun (see ShutdownFlag#guardScheduledTick).
        ShutdownFlag.guardScheduledTick(this::sweepTick,
                t -> LOG.infof("Scheduled stale-run sweep abandoned during shutdown: %s", t.getMessage()));
    }

    private void sweepTick() {
        if (shutdownSignal.isShuttingDown()) {
            return;
        }
        sweepStale(OffsetDateTime.now());
    }

    public void sweepStale(OffsetDateTime now) {
        OffsetDateTime cutoff = now.minus(staleAfter);
        for (TriggerRequest request : triggerRequestQueue.findRunning()) {
            OffsetDateTime startedAt = request.getStartedAt();
            if (startedAt != null && startedAt.isBefore(cutoff)) {
                LOG.infof("Trigger request %s running since %s is older than stale-after %s: interrupting",
                        request.getId(), startedAt, staleAfter);
                triggerRequestQueue.markInterrupted(request.getId(), SHUTDOWN_REASON);
            }
        }
    }
}
