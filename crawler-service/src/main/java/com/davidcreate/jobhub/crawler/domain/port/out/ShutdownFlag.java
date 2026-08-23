package com.davidcreate.jobhub.crawler.domain.port.out;

/**
 * CDI-free ground truth for "shutdown has begun" (ADR 0032, story #398, D1, 4th pass).
 *
 * <p>Once Quarkus starts tearing the CDI container down, invoking ANY method through an
 * injected bean reference (a client proxy), even a trivial boolean getter such as
 * {@link ShutdownSignal#isShuttingDown()}, can itself throw
 * {@code IllegalStateException("ArC container not initialized")}: the proxy has to resolve
 * the underlying contextual instance on every call, and that resolution is what fails, not
 * the method body. A guard meant to detect "the container is going away" cannot depend on the
 * very container whose death it exists to detect.
 *
 * <p>This plain static holder has no framework dependency at all, so it stays readable from
 * any thread (including one still unwinding through an interrupted crawl/enrichment call, or
 * a scheduled tick's outer boundary) regardless of whether the CDI container is still alive.
 * {@link ShutdownSignal}'s real, CDI-managed implementation flips this flag when
 * {@code ShutdownEvent} fires; recovery code that might run concurrently with or after
 * teardown reads {@link #isRaised()} directly instead of going through any injected reference.
 */
public final class ShutdownFlag {

    private ShutdownFlag() {
    }

    private static volatile boolean shuttingDown = false;

    public static void raise() {
        shuttingDown = true;
    }

    public static boolean isRaised() {
        return shuttingDown;
    }

    /**
     * Runs {@code body}; if it throws once shutdown is up, the throwable is swallowed (the
     * caller passes a {@code quietLog} callback to log a single line, no stack trace) rather
     * than rethrown, since it never reaches Quarkus's uncaught-exception handler this way. If
     * shutdown is not in progress, the throwable is rethrown unchanged so genuine bugs stay
     * visible. Intended for the outer boundary of an {@code @Scheduled} method: at that point a
     * stray {@code IllegalStateException} from an injected proxy whose CDI container is
     * already gone cannot be enumerated ahead of time, hence {@code Throwable}, not
     * {@code Exception}.
     */
    public static void guardScheduledTick(Runnable body, java.util.function.Consumer<Throwable> quietLog) {
        try {
            body.run();
        } catch (Throwable t) {
            if (isRaised()) {
                quietLog.accept(t);
                return;
            }
            if (t instanceof Error error) {
                throw error;
            }
            if (t instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(t);
        }
    }
}
