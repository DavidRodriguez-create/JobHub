# ADR 0029: Crawl-run visibility (log-level policy, per-target new counts, live progress on trigger_request)

- **Status:** Accepted
- **Date:** 2026-08-05
- **Deciders:** jobhub-architect (story #513, ticket #515)
- **Affects:** crawler-service, job-service, api-contracts (`job-service.yaml`), JobHub-ui
- **Builds on:** ADR 0003 (admin trigger requests), ADR 0006 (cooperative cancellation), ADR 0016 (crawl until N new posts)
- **Schema impact:** one forward-only migration, `db/init/058-crawler-trigger-request-progress.sql`

## Context

Story #513 reports two concrete problems with a running crawl, both observed from the
production logs of a real run.

**1. The log is dominated by SQL-level detail at INFO.** Every crawled target emits lines
like `UPSERT crawler.job_post inserted=16 updated=0` and
`UPDATE crawler.pull_target id=... status=active`. These describe a statement, not a business
event. The reporter's words: "the SQL query is better for debug option". They are useful when
diagnosing a persistence bug and noise the rest of the time.

**2. There is no intermediate feedback, in the log or in the UI.** The source clients log
`Greenhouse klaviyo: found 142 jobs`, which says nothing about how many of those 142 postings
were actually new: the number the admin cares about. The only "new" number arrives at the very
end, in `Batch complete: 9 targets visited, 103 new posts`. A crawl run can take minutes, and
during that whole window the admin screen shows `running` and nothing else. The reporter:
"it should put how many are new. I know it put afterwards the total, but it will be nice if
possible to have the intermediate feedback, and like that send to the UI, why else do we have
the useless refresh button, and auto refresh?"

The constraints that bind the design:

- `CrawlerService.crawlNext()` runs in **its own transaction per target**; `crawlBatch` loops
  **outside** any transaction. Anything written inside the per-target transaction is invisible
  to other connections until that target finishes, which can be a minute or more.
- job-service reads `crawler.trigger_request` through **its own connection and its own entity**
  (`db/init/016` grants `SELECT, INSERT`, `db/init/018` adds `UPDATE`). Progress is therefore
  only visible to the UI once it is **committed**.
- A target that fails must not roll back the progress accumulated by the targets before it.
- `crawlBatch(min, null)`, the plain scheduler path, has no trigger request at all and must
  stay a clean no-op with respect to progress.
- The user's scope decision, not to be re-opened: progress is **counters plus current target**
  persisted on the existing `crawler.trigger_request` row. **Not** a new per-target feed table.
- Both services stay Hexagonal: domain has no framework annotations, services depend on ports,
  DTOs only at boundaries, schema owned by `db/init`.

## Decision

### 1. Log-level policy: statement-level lines become DEBUG behind one switch

Per-row and per-statement persistence logging drops from INFO to DEBUG. These lines describe a
SQL statement, so they belong to the layer that issues it and to the level a developer opts into:

| File | Line | New level |
|---|---|---|
| `crawler .../adapter/out/persistence/JobPostPanacheRepository.java` | `UPSERT crawler.job_post inserted=%d updated=%d` | DEBUG |
| same | `UPDATE crawler.job_post id=%s enrichmentStatus=done` | DEBUG |
| same | `UPDATE crawler.job_post id=%s enrichmentAttempts=%d enrichmentStatus=%s` | DEBUG |
| `crawler .../adapter/out/persistence/PullTargetPanacheRepository.java` | `UPDATE crawler.pull_target id=%s status=%s` | DEBUG |
| same | `INSERT crawler.pull_target id=%s status=%s` | DEBUG |
| `crawler .../adapter/out/client/source/GreenhouseJobSourceClient.java` | `Greenhouse %s: found %d jobs` | DEBUG |
| `.../LeverJobSourceClient.java` | `Lever %s: found %d jobs` | DEBUG |
| `.../SmartRecruitersJobSourceClient.java` | `SmartRecruiters %s: found %d jobs` | DEBUG |
| `.../WorkdayJobSourceClient.java` | `Workday %s: found %d jobs` | DEBUG |
| `.../AmazonJobSourceClient.java` | `Amazon %s/%s: found %d jobs` and `Amazon: found %d jobs total` | DEBUG |

The `found N jobs` lines drop because decision 2 supersedes them with a strictly more
informative line. The Amazon per-city line is the worst offender (one line per city per run)
and is exactly what DEBUG is for.

**Stays INFO** (run-lifecycle events, at most a handful per run, each one a business fact):
`CrawlerService` "Crawling: ...", the new lines from decision 2, "Batch complete"/"Batch
cancelled", the `TriggerRequestPanacheRepository` CLAIM/UPDATE lines (one per run, they mark
the state machine transitions), and the `Maintenance: ...` lines in `JobPostPanacheRepository`
(one per maintenance pass, not per row).

**The switch.** One config key covers both demoted packages, in
`crawler-service/src/main/resources/application.properties`:

```properties
# Statement-level adapter logging (UPSERT/UPDATE/INSERT lines, per-source "found N jobs").
# INFO hides them; set CRAWLER_ADAPTER_LOG_LEVEL=DEBUG to get the SQL-level detail back
# without a rebuild (quarkus.log.min-level defaults to DEBUG).
quarkus.log.category."com.davidcreate.jobhub.crawler.adapter.out".level=${CRAWLER_ADAPTER_LOG_LEVEL:INFO}
```

It is an env-overridable category level, so a developer flips it in `.env` plus a container
recreate, and `application-dev.properties` pins it to `DEBUG` so dev mode always shows the
detail. It stays consistent with the existing logging pattern: the masking log filter
(`quarkus.log.console.filter=masking-log-filter`) still applies to every line, at any level.

### 2. The combined "found / new" line is emitted by CrawlerService, not by the source clients

Only `CrawlerService` knows both halves of the number: the source client returns a
`PullResult` with the postings it found, and `persistJobs` decides how many of those are
genuinely new. Pushing the "new" count down into the clients would leak persistence knowledge
into an inbound adapter, so the combined line is emitted where the knowledge already meets, in
the domain service. Two formats, exactly:

```java
// CrawlerService.doCrawl, INFO, inside the result.isSuccess() branch only, after
// persistJobs. Covers both the batch path and the single-target crawl(UUID) path.
LOG.infof("Crawled %s (%s): %d found, %d new",
        target.getCompanyName(), target.getSourceType(), foundPosts, newPostCount);
// Crawled Klaviyo (greenhouse): 142 found, 16 new

// CrawlerService.crawlBatch, after each completed step, INFO. Run-level, so it is emitted
// by the loop and not by doCrawl.
LOG.infof("Crawl progress: %d targets visited, %d new posts so far (target %d)",
        targetsVisited, newPostsAccumulated, minNewPostsTarget);
// Crawl progress: 3 targets visited, 47 new posts so far (target 100)
```

The `Crawled ...` line is **success-only**: it lives in the `result.isSuccess()` branch of
`doCrawl`. A failed target keeps the existing `Crawl failed for %s: %s (HTTP %s)` WARN and gets
no INFO line, because a `0 found, 0 new` line immediately after a WARN is exactly the noise this
ADR exists to remove.

The `Crawl progress:` line is emitted for every **completed** target, failures included: a failed
target still counts as visited and contributes 0 new posts. It prints the same two counters that
are persisted in decision 3, so the log and the admin screen can never tell different stories.

### 3. Live progress: committed counters plus current target on `crawler.trigger_request`

Nine nullable columns are added to the existing row (no new table, per the scope decision):

```
progress_targets_visited     INTEGER
progress_new_posts           INTEGER
progress_current_company     TEXT
progress_current_source_type VARCHAR(64)
progress_last_company        TEXT
progress_last_source_type    VARCHAR(64)
progress_last_found_posts    INTEGER
progress_last_new_posts      INTEGER
progress_updated_at          TIMESTAMPTZ
```

All nullable with no default: `progress_updated_at IS NULL` is the single, unambiguous "this
run has never reported progress" marker, which is what an enrichment row, a queued crawl and
every run that predates this feature all look like. It is deliberately distinct from
`progress_new_posts = 0`, which means "reported, and nothing new yet".

**Transaction and cadence.** The write path is a new outbound port whose implementation runs in
a `REQUIRES_NEW` transaction:

```java
// crawler .../domain/port/out/CrawlProgressRecorder.java  (framework-free)
public interface CrawlProgressRecorder {
    void markCurrentTarget(UUID triggerRequestId, String companyName, String sourceType);
    void recordTargetCompleted(UUID triggerRequestId, CrawlProgress progress);
    void clearCurrentTarget(UUID triggerRequestId);
}
```

`REQUIRES_NEW` is the load-bearing choice, and it satisfies every constraint at once:

- **Committed as the run proceeds.** The suspended caller's transaction does not gate the
  write, so the row is updated and committed the moment each call returns. job-service's
  connection sees it on the next poll.
- **`markCurrentTarget` is callable from inside the per-target transaction.** It is invoked in
  `crawlNext` right after the target is locked, before the slow HTTP work starts, so the UI
  shows "Crawling Klaviyo (greenhouse)" during the minute that target takes, instead of after.
  A plain write there would have stayed invisible until the target finished, which is the whole
  problem.
- **A failing target cannot roll back progress.** Progress lives in a separate, already
  committed transaction. If `doCrawl` throws and the per-target transaction rolls back, the
  counters from previous targets and the current-target marker survive.
- **The scheduler path is a clean no-op.** `crawlBatch(min, null)` passes a null
  `triggerRequestId`; `CrawlerService` guards on null before calling the port, and the adapter
  guards again defensively. No trigger request, no write, no behaviour change.

Cadence, in order, once per target: `markCurrentTarget` (from `crawlNext`, immediately after
the lock) then `recordTargetCompleted` (from `crawlBatch`, right after the step returns,
carrying the running counters and the just-finished target's found/new pair, and clearing the
current-target fields). When the loop exits for any reason, `clearCurrentTarget` runs so a
finished run never claims to be crawling something. `markRunning` zeroes the counters at run
start, and `markDone`/`markCancelled` leave the final counters in place: `result_summary`
remains the authoritative end-of-run text.

**Progress writing must never break a crawl.** The adapter wraps each write in a try/catch,
logs at WARN and swallows. Visibility is strictly less important than the crawl itself. Note
that `REQUIRES_NEW` holds a second pooled connection for the duration of the write; the writes
are single-statement updates and the default Agroal pool (20) absorbs this comfortably.

**Grants.** None needed. `db/init/016` and `018` grant `SELECT, INSERT, UPDATE` on the table,
and PostgreSQL table-level privileges cover columns added later. The migration says so in a
comment so no one re-grants out of superstition.

### 4. job-service maps the progress columns read-only

job-service's `TriggerRequestEntity` maps the same physical table. Its cancel path
(`AdminTriggerService.cancel` to `TriggerRequestPanacheRepository.update`) mutates a managed
entity, and Hibernate's default UPDATE writes **every** mapped column, not only the dirty ones.
If job-service mapped the progress columns normally, a cancel issued mid-run would write back
whatever progress values job-service happened to read a moment earlier, clobbering the live
counters crawler-service had advanced in between. The new columns are therefore mapped with
`@Column(..., insertable = false, updatable = false)`: a read-only projection, which excludes
them from every INSERT and UPDATE job-service issues. crawler-service stays the only writer.

### 5. Contract shape

`TriggerRunInfo` gains `progress`, a new `TriggerProgress` schema in
`api-contracts/src/main/resources/openapi/job-service.yaml`, marked
`x-implementation-status: planned`. Field names, frozen:
`targetsVisited`, `newPosts`, `currentCompany`, `currentSourceType`, `lastCompany`,
`lastSourceType`, `lastFoundPosts`, `lastNewPosts`, `updatedAt`, with
`required: [targetsVisited, newPosts]`. `progress` is null when nothing has been reported,
which the UI must handle: it is the normal state for enrichment runs and for every run that
existed before this change.

## Consequences

**Positive.** A run is legible while it runs, in both channels, from the same numbers. The
Refresh button and the 5s auto-poll finally have changing data behind them. Statement-level
detail is one env var away rather than deleted. No new table, no new endpoint, no new polling
mechanism: the existing `GET /jobs/admin/triggers/status` payload simply carries more.

**Negative / accepted.** Progress lags by up to one target, because it is written between
targets: a slow Workday board can hold `updatedAt` steady for a minute, and the UI should show
staleness rather than pretend the run is stuck. Enrichment runs report no progress in this
iteration (the columns exist and stay null; extending `EnrichmentService` the same way is a
follow-up). Two services now map the same widened table, so the two `TriggerRequestEntity`
classes must be widened together in the same PR or Hibernate `validate` fails on the one left
behind. The `REQUIRES_NEW` writes add roughly two extra short transactions per crawled target,
which is negligible next to an HTTP fetch plus a bulk upsert.

**Alternatives rejected.** A per-target feed table (`crawler.crawl_run_target`) was ruled out
by the user's scope decision: it is a richer history but it needs a new table, a new grant, a
new endpoint and a retention policy for a screen that only ever shows the latest line. Writing
progress inside the per-target transaction was rejected because it is invisible until the
target commits, which defeats the purpose. A server-sent-events or WebSocket stream from
crawler-service was rejected because crawler-service publishes no port at all today (it is a
background service with no exposed HTTP surface) and the UI already polls.

## References

- Story #513, sub-issues #515 (this design), #518 (crawler-service), #519 (job-service), #520 (UI)
- `db/init/016-crawler-trigger-request.sql`, `db/init/018-crawler-trigger-cancel.sql`
- ADR 0003 (admin trigger requests), ADR 0006 (cooperative cancellation), ADR 0016 (crawl until N new posts)
