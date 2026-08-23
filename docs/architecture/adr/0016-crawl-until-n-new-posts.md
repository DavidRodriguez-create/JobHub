# ADR 0016: Crawl until N new posts, not N sources

- **Status:** Accepted
- **Date:** 2026-07-03
- **Deciders:** jobhub-architect, David R H
- **Affects:** crawler-service (story #263, sub-issue #264)

## Context

`crawler-service` is Hexagonal. `CrawlerService.crawlBatch(int limit)` today loops
`while (count < limit)`, incrementing `count` once per TARGET crawled by `crawlNext()`.
`limit` is a target/source count (`crawler.crawl.batch-size`, default 10; prod 70). A run
stops when it has visited `limit` targets, when no target is available
(`PullTargetRepository.findNextAvailableAndLock()` returns empty, respecting the
post-success 1h cooldown), or on trigger cancellation.

Story #263 changes the goal: a run must keep crawling until it has collected at least N
genuinely NEW job posts (target N=100, "regardless of source"), OR there are no more
sources to crawl. This applies to BOTH the scheduled crawl and the trigger/REST crawl.

The genuinely-new set already exists at runtime: `doCrawl()` to `persistJobs()` computes
`newByUrl` (postings not matched by content-hash then URL) but currently discards its size.
So the count is runtime-computed: no schema change, no api-contracts change (the `/crawl`
endpoint is hand-written JAX-RS; there is no `crawler-service.yaml`).

Constraints: the domain must stay framework-free (Hexagonal, zero JPA/CDI below the ports),
and each `crawlNext()` must keep its own `@Transactional` boundary so every target commits
independently. The accumulator therefore lives in the caller loop, not inside a transaction.

## Decision

We will make a crawl run terminate on cumulative NEW posts, not on targets visited, in both
entry points, with whole-source granularity and a safety cap on targets visited.

1. **New-count flow (domain-internal, framework-free).**
   - `persistJobs(List<JobPost>)` returns `int` (the number of genuinely-new postings
     inserted this pull).
   - `doCrawl(PullTarget)` surfaces that count. It currently returns `PullResult`; add a
     small immutable domain value `CrawlOutcome { PullResult result, int newPosts }` (under
     `domain/model/`, `@Getter @Builder`, no annotations) and return it. On a failed pull,
     `newPosts = 0`. The REST single-target `crawl(UUID)` path ignores the count.
   - `crawlNext()` returns the per-step new count instead of a boolean. Change its signature
     to `Optional<Integer> crawlNext()`: `Optional.of(newPosts)` when a target was crawled,
     `Optional.empty()` when no target was available. This keeps the "no target left" signal
     distinct from "crawled but produced 0 new posts". `crawlNext()` keeps `@Transactional`.
   - `crawlBatch(...)` owns an `int newPosts` accumulator in the loop body. Because the
     accumulator is a local variable in the non-transactional `crawlBatch` method, it
     survives naturally across the separate `crawlNext()` transactions; each committed
     transaction returns its count up to the loop, which adds it in.

2. **Stop rule for `crawlBatch(int minNewPosts, UUID triggerRequestId)`.** Loop:
   - if `triggerRequestId != null` and cancel requested -> stop, `cancelled = true`.
   - if targets visited `>= maxTargetsPerRun` (safety cap, see below) -> stop.
   - call `crawlNext()`; if empty (no available target) -> stop.
   - else add its count to `newPosts`, increment `targetsVisited`.
   - **after** adding, if `newPosts >= minNewPosts` -> stop.
   The `newPosts >= minNewPosts` check is evaluated only between whole `crawlNext()` steps,
   so a source is always crawled in full: the run stops AFTER the source that pushed the
   cumulative total over the target, never mid-source.

3. **Config.**
   - New key `crawler.crawl.min-new-posts`, default `100`. This is the per-run new-post
     target and is what the scheduler and trigger pass to `crawlBatch`.
   - New key `crawler.crawl.max-targets-per-run`, default `200`. Safety cap on targets
     visited per run (see Consequences for the rationale). Bounds outbound HTTP work when
     few new posts exist.
   - `crawler.crawl.batch-size` (targets) is **removed** as the driver of `crawlBatch`.
     `CrawlerScheduler` and `TriggerRequestScheduler` now inject `min-new-posts`, not
     `batch-size`. (The key may be deleted from properties files; it has no other consumer.)
   - `crawler.crawl.max-batch-size` (the old validation bound on the `int` argument) is
     **repurposed and renamed conceptually** into `max-targets-per-run` above. The
     `crawlBatch` argument validation changes to guard `min-new-posts`:
     `if (minNewPosts < 1) throw ValidationException`. There is no upper bound on the target
     count itself (the run is bounded by cooldown-exhaustion and by `max-targets-per-run`).
   - **REST `POST /crawl?limit=`** is reinterpreted: `limit` becomes the min-new-posts target
     for that ad-hoc run (default changed from `10` to the configured `min-new-posts`, i.e.
     `100`). It is validated `>= 1` only. The old "targets" meaning is dropped. The
     `max-targets-per-run` safety cap still applies to REST runs.

4. **`CrawlBatchResult` shape (frozen).** Add `int newPosts`. Keep `crawled` renamed in
   meaning to "targets visited" (kept for observability; field name stays `crawled` to avoid
   churn, documented as targets-visited). `isEmpty()` returns `newPosts == 0 && crawled == 0`
   so the REST `NO_CONTENT` path fires only when nothing at all happened. New shape:
   `{ int crawled /*targets visited*/, int newPosts, boolean hasMore, boolean cancelled }`.

5. **No `db/init` migration and no api-contracts YAML change.** The new count is
   runtime-computed; `CrawlBatchResult` is an internal domain/JSON DTO, not contract-owned.

## Consequences

- Positive: crawl output is measured by the thing that matters (new postings), consistent
  across scheduled and triggered runs; no schema or contract change; layering preserved.
- Positive: whole-source granularity keeps each source's cooldown accounting correct and
  avoids half-crawled sources.
- Negative / cost: a run can now visit many sources when new posts are scarce. The
  `max-targets-per-run` cap (default 200) bounds that: without it a low-yield run would walk
  every available source each cron tick, hammering boards and spending LLM/HTTP budget for
  little gain. 200 is comfortably above the current source count while still a hard ceiling.
- Negative / cost: the `crawlNext()` signature change (`boolean` -> `Optional<Integer>`) and
  the `CrawlBatchResult` field change touch the unit + component tests; those must be updated.
- Follow-ups: PDA writes the functional spec; QAE updates test cases (stop-on-target,
  stop-on-exhaustion, stop-on-cap, whole-source-not-mid-source, cancellation); backend dev
  implements. Update summary strings (below) and remove `crawler.crawl.batch-size` from
  `application-dev.properties` / `application-prod.properties`.

## Alternatives considered

- **Keep `batch-size` (targets) and add `min-new-posts` as a second stop condition** —
  rejected: two overlapping bounds with different units is confusing; the story wants the
  new-post target to be the primary driver, with only a safety cap in target units.
- **No safety cap (rely only on cooldown-exhaustion to bound a run)** — rejected: a
  low-yield period would make every scheduled run traverse all sources, defeating the point
  of polite, cooldown-paced crawling and spiking outbound HTTP.
- **Check the target mid-source (stop as soon as `newPosts >= target` within a pull)** —
  rejected: a pull is atomic at the client level; splitting it complicates cooldown/state
  and gains nothing. Whole-source granularity is simpler and correct.
- **Move `CrawlBatchResult` into an api-contracts YAML** — rejected: `/crawl` is an internal
  hand-written endpoint with no spec; contract-first does not apply to it.
