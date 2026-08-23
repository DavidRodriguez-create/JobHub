package com.davidcreate.jobhub.crawler.domain.port.out;

/**
 * Shared shutdown signal (ADR 0032, story #398). One implementation observes Quarkus's
 * {@code ShutdownEvent}; all three schedulers and the batch loops in {@code CrawlerService}/
 * {@code EnrichmentService} check it at the next item boundary and stop starting new work
 * once it is raised, rather than dispatching into a closing EntityManagerFactory.
 *
 * <p>{@link #workStarted()}/{@link #workFinished()} let a batch call register that it is
 * in flight for the whole of {@code crawlBatch}/{@code enrichPending}, not just per item: the
 * real adapter blocks its {@code ShutdownEvent} observer on this count (bounded by a timeout)
 * so the EntityManagerFactory does not close underneath a run that is still executing on a
 * worker thread. Default no-ops here keep {@code ShutdownSignal} a single-method functional
 * interface for the {@code () -> false} default used when no run is in progress.
 */
public interface ShutdownSignal {

    boolean isShuttingDown();

    default void workStarted() {
    }

    default void workFinished() {
    }
}
