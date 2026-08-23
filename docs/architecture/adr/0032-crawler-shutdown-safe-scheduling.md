# ADR 0032: Shutdown-safe crawler scheduling and trigger-run visibility

- **Status:** Proposed
- **Date:** 2026-08-11
- **Deciders:** jobhub-architect, product owner (story #398)
- **Affects:** crawler-service, job-service, api-contracts, db/init

## Context

Story #398. crawler-service observes no `ShutdownEvent`, so its three schedulers dispatch during
teardown and hit a closed EntityManagerFactory. A kill between `markRunning` and `markDone` strands
`crawler.trigger_request.status = 'running'` forever, and since `TriggerRequestScheduler` skips
ENRICHMENT while `hasRunning(CRAWL)`, one stranded row disables admin enrichment permanently.
Product also wants a distinct "no targets" outcome, no auto/manual collision, last-run time plus
origin per kind, and an accepted phase in the trigger UI. Both services stay Hexagonal.

## Decision

**Shutdown guard.** One shared `ShutdownSignal` out-port (`domain/port/out/`), implemented by a
single `@ApplicationScoped` adapter observing `ShutdownEvent` and flipping a volatile flag. All
three schedulers return immediately when it is set; `crawlBatch` and `enrichPending` check it at the
item boundary next to the existing cancel check, so no new external call or transaction starts. One
bean, not per-scheduler flags: the domain loops need it too and must not see a framework event.

**Reaper: both.** A `StartupEvent` observer transitions every non-terminal row to `failed`
(`outcome = interrupted`, reason "interrupted by shutdown"); the 10s poll additionally sweeps
`running` rows older than `crawler.trigger.stale-after` (default `PT2H`). Startup is the guarantee,
the sweep covers a wedged live process, a down-path mark is best effort only.

**N2 exclusion.** The scheduled crawl enqueues and claims a real `trigger_request` row with
`origin = 'scheduled'`; a partial unique index on `kind` over active statuses makes "one active run
per kind" a database fact. Automatic yields to manual: the scheduled pass skips its tick when any
crawl row is active. A manual request never yields, it waits `queued`.

**N1.** New `outcome` column and contract enum (`completed`, `no_targets`, `cancelled`,
`interrupted`, `failed`), so "no more targets to crawl" is machine-readable, not parsed prose.
Status stays `succeeded`.

**Origin.** Write side: `crawler.trigger_request.origin`. Read side: job-service already maps that
table cross-schema, so it just maps the new columns; the `job` schema is unchanged.

**N4.** No new persisted status. `requesting` is UI-local optimistic state until the 202, then the
existing `queued` means accepted.

## Consequences

- Positive: clean shutdown, no wedged enrichment, one lock for both crawl origins.
- Cost: automatic crawls now write trigger rows (more rows, visible in admin history).
- Follow-ups: db/init 059 to 060 (#563); job-service needs no migration (#564).

## Alternatives considered

- **Per-scheduler `ShutdownEvent` observers** rejected: leaves the domain loops unguarded.
- **In-memory auto/manual mutex** rejected: does not survive restart or a second instance.
- **New `accepted` status value** rejected: `queued` already means accepted-not-started.
