# ADR 0006: Cooperative cancellation for admin-triggered crawl and enrichment passes

- **Status:** Accepted
- **Date:** 2026-06-12
- **Deciders:** Principal Architect (jobhub-architect); David R H
- **Affects:** job-service (Hexagonal), crawler-service (Hexagonal), api-contracts
  (job-service.yaml), db/init, JobHub-ui

## Context

Story #58 / ticket #70 asks for a way to **gracefully stop** an in-progress or queued
admin-triggered crawl or enrichment pass from the admin UI.

The current trigger system (ADR 0003) has a one-way lifecycle: `queued -> running ->
succeeded | failed`. Once an admin triggers a pass, it runs to completion. The batch loops
in `CrawlerService.crawlBatch()` and `EnrichmentService.enrichPending()` iterate item by
item and are synchronous on the scheduler thread. There is no abort mechanism.

Two properties of the system make cancellation straightforward:

1. **Both batch loops are iterative** -- `crawlBatch` loops via `while (count < limit) {
   crawlNext(); }`, and `enrichPending` loops via `for (JobPost job : pending)`. Inserting a
   cancellation check between iterations is natural and safe -- no partial item is left behind.
2. **The `trigger_request` table is already the shared signal channel** between job-service
   and crawler-service. The admin cannot reach crawler-service directly (no published port,
   no JWT). Reusing the table for the cancel signal follows the existing pattern.

Cron-based scheduled runs (`CrawlerScheduler`, `EnrichmentScheduler`) call the use cases
directly without a `trigger_request` row. They are not cancellable through this mechanism.
This is acceptable: cron batches use small, fixed batch sizes and complete quickly. A future
global stop-flag could extend cancellation to cron runs if needed.

## Decision

We will implement **cooperative cancellation via DB-polled status** on the existing
`crawler.trigger_request` table, with two new status values and one new REST endpoint.

### 1. New status values

The `status` column gains two values:

- **`cancel_requested`** -- transitional signal. Set by job-service when the admin requests
  cancellation. Tells the crawler to stop after its current item.
- **`cancelled`** -- terminal state. Set by crawler-service when it observes
  `cancel_requested` and stops work, or set immediately by job-service for `queued` rows
  (which the crawler has not yet claimed).

Updated state machine:

```
queued ──────┬──> running ──────┬──> succeeded
             |                  ├──> failed
             |                  └──> cancel_requested ──> cancelled
             └──> cancelled (immediate, row was never claimed)
```

### 2. Cancel endpoint (job-service)

`POST /jobs/admin/triggers/{kind}/cancel` -- `@RolesAllowed("admin")`.

Behavior:
- Finds the active (`queued` or `running`) `trigger_request` row for the given kind.
- If `queued`: transitions directly to `cancelled` (sets `finished_at = now()`,
  `result_summary = "Cancelled before execution"`). Returns **200** with the updated row.
- If `running`: transitions to `cancel_requested`. Returns **200** with the updated row.
  The crawler will detect this on its next iteration and finalize to `cancelled`.
- If no active row exists: returns **409** with `error: "No Active Trigger"`.

job-service needs `UPDATE` on `crawler.trigger_request` (currently only `SELECT, INSERT`).
Migration `018-crawler-trigger-cancel.sql` grants this.

### 3. Cancellation detection in crawler-service (cooperative polling)

The crawler's batch loops (in `CrawlerService` and `EnrichmentService`) already iterate
item by item. Between iterations, each loop calls a new port method
`TriggerRequestQueue.isCancelRequested(UUID id)` which reads the row's status. If
`cancel_requested`, the loop breaks, and the caller (`TriggerRequestScheduler.execute()`)
calls `TriggerRequestQueue.markCancelled(UUID id, String resultSummary)` to finalize.

The cancellation check is a single `SELECT status FROM crawler.trigger_request WHERE id = ?`
per loop iteration. Given that crawl iterations already involve HTTP calls to external job
boards (seconds each) and enrichment iterations involve LLM API calls (seconds each), one
extra indexed PK lookup per iteration adds negligible overhead.

**Alternative rejected: `AtomicBoolean` in-process flag.** An `AtomicBoolean` would avoid
the per-iteration DB read, but it requires an inbound signal path to the crawler JVM (HTTP
endpoint or JMX), which violates the "no published port" invariant (ADR 0003). The DB
status column is the existing, proven signal channel.

### 4. Port and domain changes

**crawler-service (Hexagonal):**

- `TriggerRequestQueue` (out-port): add `boolean isCancelRequested(UUID id)` and
  `void markCancelled(UUID id, String resultSummary)`.
- `CrawlUseCase` and `EnrichJobsUseCase` (in-ports): extend to accept an optional
  trigger request ID so they can poll for cancellation. The simplest approach: add an
  overload `crawlBatch(int limit, UUID triggerRequestId)` and
  `enrichPending(int limit, UUID triggerRequestId)`, or pass the trigger request ID
  via a callback/checker interface. The implementer decides the cleanest signature.
- `TriggerStatus` (domain enum): add `CANCEL_REQUESTED` and `CANCELLED`.
- `TriggerRequestScheduler.execute()`: after the batch call returns, check whether
  the result was a cancellation (the use case can return a result indicating early
  stop due to cancellation, or the scheduler can check `isCancelRequested` itself)
  and call `markCancelled()` instead of `markDone()`.

**job-service (Hexagonal):**

- `AdminTriggerUseCase` (in-port): add `TriggerRequest cancel(TriggerKind kind)`.
- `AdminTriggerService`: implement cancel -- find the active row via the repository,
  decide `queued -> cancelled` vs `running -> cancel_requested`, persist, return.
- `TriggerRequestRepository` (out-port): add `Optional<TriggerRequest> findActive(TriggerKind kind)`
  and `TriggerRequest updateStatus(UUID id, TriggerStatus newStatus)` (or similar).
  The existing `hasActiveRequest` can be refactored to use `findActive`.
- `TriggerStatus` (domain enum): add `CANCEL_REQUESTED` and `CANCELLED`.
- New domain exception: `NoActiveTriggerException` (maps to 409 via ExceptionMapper).
- `JobResource`: implement `cancelTrigger(String kind)` from the generated `JobsApi`.

### 5. Contract changes (frozen)

**job-service.yaml** (additive):

- `TriggerStatusValue` enum: add `cancel_requested`, `cancelled`.
- New path: `POST /jobs/admin/triggers/{kind}/cancel` -- operationId `cancelTrigger`,
  `x-implementation-status: planned`.
- Responses: 200 `TriggerResponse`, 401, 403 `ErrorResponse`, 409 `ErrorResponse`, 500.

### 6. Migration

`db/init/018-crawler-trigger-cancel.sql` (crawler range 010-019, next free: 018):
- Drop and recreate the status CHECK to include `cancel_requested` and `cancelled`.
- `GRANT UPDATE ON crawler.trigger_request TO job_user`.

No job-service-range migration needed (no job-schema changes).

### 7. UI changes (out of scope for this ADR, but outlined)

The admin UI `TriggerKindPanel` will:
- Show a "Stop" button when `runInfo.status` is `running` or `queued`.
- Call `POST /jobs/admin/triggers/{kind}/cancel` on click.
- Display `cancel_requested` as "Cancelling..." and `cancelled` as "Cancelled" in the
  status display.
- The existing poll loop (5s) will pick up the `cancelled` terminal state.

### 8. Tests

**job-service:**
- Unit: `AdminTriggerServiceTest` -- cancel queued (direct to cancelled), cancel running
  (to cancel_requested), cancel when no active row (throws `NoActiveTriggerException`).
- Component: `AdminTriggerResourceComponentTest` -- 200 cancel, 409 no active, 401/403.

**crawler-service:**
- Unit: `TriggerRequestSchedulerTest` -- verify execute() checks cancellation between
  iterations and calls `markCancelled`.
- Unit: `CrawlerServiceTest` -- batch loop exits early when cancellation check returns true.
- Unit: `EnrichmentServiceTest` -- same for enrichment loop.
- Component: `TriggerRequestPanacheRepositoryComponentTest` -- `isCancelRequested` and
  `markCancelled` persistence.

## Consequences

- Positive: cancellation reuses the existing DB-polled signal channel, requiring no new
  infrastructure, no published port on crawler-service, and no new inter-service protocol.
- Positive: cooperative cancellation is safe -- no item is left half-processed. The loop
  finishes its current item, then stops.
- Positive: the `cancel_requested -> cancelled` two-phase model lets the UI show
  intermediate state ("stopping...") and the crawler confirm completion.
- Negative / cost: one extra PK-lookup per batch-loop iteration (negligible vs. the HTTP/LLM
  call each iteration already makes).
- Negative / cost: cron-based runs are not cancellable. Acceptable for now; a future ADR can
  add a global stop-flag if needed.
- Negative / cost: job-service now needs `UPDATE` on `crawler.trigger_request`, widening its
  grant from read+insert to read+insert+update. Still minimal privilege -- no DELETE, no DDL.
- Follow-ups: job-service ticket #73 (cancel endpoint + port + exception + mapper + tests),
  crawler-service ticket #74 (cancellation check in loops + port methods + scheduler update +
  tests), UI ticket (Stop button + new status display).

## Alternatives considered

- **`AtomicBoolean` in-process flag set via an internal HTTP endpoint on crawler-service** --
  rejected because it requires crawler to publish a port and add JWT verification, violating
  the deploy invariant established in ADR 0003. The DB status column is the existing signal
  path and adds no new infrastructure.
- **Message queue (e.g. AMQP/Redis pub-sub) for the cancel signal** -- rejected because
  JobHub has no message broker in its stack. Introducing one for a single cancel signal is
  disproportionate. The DB poll is simple, proven, and already used for the trigger itself.
- **Kill the crawler process (SIGTERM) and let the container orchestrator restart it** --
  rejected because it is not graceful (current item may be lost), requires orchestrator
  access, and would interrupt all cron-based work as well, not just the targeted trigger.
- **Cancel via a separate `cancel_request` table instead of new status values** -- rejected
  because it splits the state machine across two tables. Adding values to the existing
  `status` column is simpler, atomic, and keeps the single-table invariant.
- **Synchronous cancel (job-service directly stops the crawler via RPC)** -- rejected for the
  same reason as ADR 0003's alternative (A): crawler has no published port.
