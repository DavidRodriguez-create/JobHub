package com.davidcreate.jobhub.crawler.domain.port.out;

import com.davidcreate.jobhub.crawler.domain.model.TriggerKind;
import com.davidcreate.jobhub.crawler.domain.model.TriggerOrigin;
import com.davidcreate.jobhub.crawler.domain.model.TriggerRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TriggerRequestQueue {

    /**
     * Atomically claim the oldest {@code queued} row of the given kind and
     * transition it to {@code running}. Dedup is enforced per kind: only one
     * row per kind can ever be claimed/running at a time.
     */
    Optional<TriggerRequest> claimNext(TriggerKind kind);

    void markRunning(UUID id);

    /**
     * Transitions the row to a terminal state with the given status/outcome.
     *
     * @param outcome one of {@code TriggerOutcome#value()} (completed, no_targets, failed,
     *                interrupted); {@code null} is allowed for legacy call sites that do not
     *                yet compute one.
     */
    void markDone(UUID id, String status, String outcome, String resultSummary, String errorReason);

    /**
     * Returns {@code true} if there is a {@code trigger_request} row of the given
     * {@code kind} currently in {@code running} status.
     */
    boolean hasRunning(TriggerKind kind);

    /**
     * Returns {@code true} if there is a {@code trigger_request} row of the given
     * {@code kind} currently {@code queued} or {@code running} (story #398, N2): the
     * scheduled crawl yields its own tick whenever any crawl run, of either origin, is
     * already active.
     */
    boolean hasActive(TriggerKind kind);

    /**
     * Inserts a new {@code queued} row for the given kind/origin and returns the
     * persisted row. Used by {@code CrawlerScheduler} to record the automatic
     * scheduled crawl as a real trigger request (story #398, N2), so it is
     * claimed/executed through the same pipeline as an admin-triggered run, and by
     * {@code TriggerRequestQueueService} (story #582, ADR 0033) to queue a request on
     * behalf of the internal {@code /internal/trigger-requests} endpoint.
     *
     * @throws com.davidcreate.jobhub.crawler.domain.exception.ConflictException if a
     *         {@code queued} row of this kind already exists, breaching the partial
     *         unique index {@code uq_trigger_request_active_kind_status}
     *         ({@code db/init/060}).
     */
    TriggerRequest enqueue(TriggerKind kind, TriggerOrigin origin, UUID requestedBy);

    /**
     * Cancels the currently active ({@code queued} or {@code running}) row of the
     * given kind (story #582, ADR 0033). A {@code queued} row goes straight to the
     * terminal {@code cancelled}, stamping {@code finished_at} and
     * {@code result_summary}. A {@code running} row goes to the transitional
     * {@code cancel_requested}; the batch loop observes it and stops. Returns
     * {@link Optional#empty()} if no active row of this kind exists.
     */
    Optional<TriggerRequest> cancelActive(TriggerKind kind);

    /**
     * Returns every row currently {@code running}, for the stale-run sweep
     * (story #398, D2).
     */
    List<TriggerRequest> findRunning();

    /**
     * Transitions a single {@code running} row to a terminal, interrupted state:
     * {@code status = failed}, {@code outcome = interrupted}, {@code error_reason = reason}.
     */
    void markInterrupted(UUID id, String reason);

    /**
     * Bulk-transitions every non-terminal row ({@code queued}, {@code running},
     * {@code cancel_requested}) to {@code failed}/{@code interrupted} with the given reason.
     * Called once on startup (story #398, D2): the guarantee half of the reaper.
     */
    void reapNonTerminal(String reason);

    /**
     * Returns {@code true} if the {@code trigger_request} row with the given {@code id}
     * is currently in {@code cancel_requested} status. Used by the batch loops in
     * {@code CrawlerService}/{@code EnrichmentService} to cooperatively stop between
     * iterations.
     */
    boolean isCancelRequested(UUID id);

    /**
     * Transitions the {@code trigger_request} row with the given {@code id} to
     * {@code cancelled}, stamping {@code finished_at} and the given
     * {@code resultSummary}. {@code error_reason} is left {@code null}.
     */
    void markCancelled(UUID id, String resultSummary);
}
