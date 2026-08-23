# ADR 0018: Best-effort totals for job search (estimate-above-threshold + short-TTL cache)

- **Status:** Accepted
- **Date:** 2026-07-19
- **Deciders:** jobhub-architect (story #331 / sub-issue #377)
- **Affects:** job-service, api-contracts (job-service.yaml), JobHub-ui (default filter, #381)

## Context

Every `GET /jobs` search runs two queries against `crawler.job_post`: `search()` fetches one
page, and `count(JobSearchQuery)` runs a second `SELECT COUNT(j)` over the **identical** filter
set to populate the `JobSearchPage` wrapper (`totalElements`/`totalPages`) the UI consumes. That
filter set is not cheap to count: it includes a full-text predicate
(`search_vector @@ plainto_tsquery('english', :keyword)`), correlated `EXISTS` subqueries over
the `job_post_location` child table (ADR 0017), array-overlap on `languages`, and range
predicates. Fetching a page stops after `size` rows (`setMaxResults`), but `COUNT(*)` must
evaluate the whole predicate over every matching row — so counting routinely costs **more** than
the page fetch it accompanies. Story #328 baselines and `pg_stat_statements` (migration 047)
confirmed the count query as a top offender.

Two properties of the count make exactness cheap-to-give-up:
- The count is expensive **precisely when the result set is large** — a broad, lightly-filtered
  search. At that size, users do not need an exact number ("about 4,500 jobs" is as useful as
  "4,512"); job boards universally show "1,000+".
- The count is **cheap when the result set is small**, and small is exactly when exactness
  matters ("3 results").

Constraints: contract-first (the pagination signal lives in the `JobSearchPage` body, not an
`X-Total-Count` header — the story's header framing is legacy naming); backward-compatible for the
existing UI wrapper (`JobHub-ui/src/api/jobs.js` reads `totalElements`/`totalPages` and ignores
unknown fields); schema owned by `db/init` SQL; job-service is Hexagonal (the strategy lives
behind the existing `JobPostRepository.count` port — no new port shape).

## Decision

We will make job-search totals **best-effort** via a two-layer strategy behind the existing
`JobPostRepository.count(JobSearchQuery)` port, configurable via a `job.search.count.*` prefix.

1. **Estimate-above-threshold (primary).** For a search, first obtain the PostgreSQL planner's
   row estimate for the filtered query (an `EXPLAIN (FORMAT JSON)` of the count/search predicate
   — planning only, no execution). If the estimate is at or below
   `job.search.count.exact-threshold`, run the exact `SELECT COUNT(j)` (cheap, because the set is
   small) and return it as exact. If the estimate is above the threshold, **return the estimate**
   and skip the exact count. This bounds worst-case count cost to a planning call regardless of
   filter breadth, keeps small result sets exact, and needs no schema change (existing indexes
   from migrations 010/014/017 already serve the predicates; `idx_job_post_first_seen_at` already
   serves the new 3-day default).

2. **Short-TTL count cache (secondary).** Cache the count result (value + `isEstimate` flag)
   keyed on the **filter set only** — deliberately excluding `page`, `size`, and `sort`, since the
   count is independent of them. This makes the dominant repeated-request patterns — paging
   `0,1,2,…` through one filter, and the shared default listing hit by every anonymous visitor —
   compute the count once and reuse it for `job.search.count.cache.ttl`. Bounded by
   `job.search.count.cache.max-size` (LRU eviction). The cache is a load-shedder, not the primary
   mechanism: it absorbs bursts but does not reduce first-hit worst case (that is layer 1's job).

3. **Explicit estimate signal.** Add an optional, additive boolean `countIsEstimate` to the
   `JobSearchPage` schema (default `false`, `x-implementation-status: planned`). When `true`,
   `totalElements`/`totalPages` are planner estimates. Chosen over an extra HTTP header because
   the pagination signal already lives in the response body and travels with the client-side query
   cache (#329/#370); chosen over silent best-effort because the UI should be free to render a
   "~4,500" / "4,500+" affordance and to stop paging on the first empty page rather than trusting
   `totalPages`.

Mode is governed by `job.search.count.mode` (`exact | estimate | hybrid`, default `hybrid`),
giving ops an instant, fully backward-compatible fallback to the old exact-count behaviour
(`exact`) without a redeploy.

### Config keys (for backend ticket #380)

| Key | Default | Meaning |
|---|---|---|
| `job.search.count.mode` | `hybrid` | `exact` = always exact `COUNT` (legacy behaviour); `estimate` = always planner estimate; `hybrid` = estimate-above-threshold (this ADR). |
| `job.search.count.exact-threshold` | `1000` | Planner-estimate row count at/below which an exact `COUNT` is run; above it the estimate is returned. |
| `job.search.count.cache.enabled` | `true` | Toggle the short-TTL count cache (layer 2). |
| `job.search.count.cache.ttl` | `PT30S` | Max staleness of a cached count. Short by design; counts drift slowly relative to crawl cadence. |
| `job.search.count.cache.max-size` | `1000` | Max distinct filter-sets cached (LRU eviction); bounds memory. |

### UI default (for frontend ticket #381)

The "last 3 days" default listing is **frontend-only**: change `postedFilter` initial state in
`JobHub-ui/src/screens/JobSearch.jsx` from `"any"` to `"3days"`, which `POSTED_MAP` already maps
to the existing `postedWithin=3d` contract enum value. **No backend or contract change.** The
backend default stays "omitted = all time": making the server default to 3 days would silently
change API semantics for every consumer of a bare `GET /jobs` and contradicts the contract, which
documents `postedWithin` as an explicit optional filter with no default. Bonus: the 3-day default
shrinks the most common query's result set, so it will often fall at/below `exact-threshold` and
get a cheap exact count anyway.

### Migration range

**No DDL.** The estimate path is `EXPLAIN`-only, and every predicate (including the 3-day
`first_seen_at >= now-3d` default, served by the existing `idx_job_post_first_seen_at`) is already
indexed by migrations 010/014/017. Highest existing `db/init` number is `047`; no new file is
claimed by this story. (If baselining later shows stale planner stats degrade estimate quality,
an autovacuum/`ANALYZE`-tuning follow-up would take the next free number, `048`.)

## Consequences

- Positive: worst-case count cost drops from "scan every matching row" to "plan the query" for
  broad searches; repeated/paged/default requests are served from a short-TTL cache; small result
  sets stay exact where exactness is user-visible.
- Positive: fully backward-compatible — additive optional field; `mode=exact` restores legacy
  behaviour without a contract change.
- Negative / cost: `totalElements`/`totalPages` become approximate above the threshold; clients
  must treat `totalPages` as a guide and stop on the first empty page. Planner estimates depend on
  fresh table statistics (autovacuum/`ANALYZE`); very stale stats can skew large-count displays.
- Negative / cost: a new config surface and a bounded in-memory cache to maintain in job-service.
- Follow-ups: backend #380 implements the strategy behind `JobPostRepository.count` (keep the port
  signature; the estimate flag can ride on a small return type or a sibling method — dev's call
  within Hexagonal layering); frontend #381 flips the default and MAY render the estimate
  affordance; QAE covers exact-below-threshold, estimate-above-threshold, cache hit/miss, and the
  `mode=exact` fallback.

## Alternatives considered

- **TTL cache of exact totals only (Radar option a), no estimate.** Rejected as the *primary*
  mechanism: the cache key includes free-text `keyword`, so hit rate on keyword searches is near
  zero, and every first request for a filter combo still pays the full exact-count cost — it defers
  cost, it does not bound it. Kept as the secondary layer where it genuinely wins (pagination and
  the shared default listing).
- **Planner estimate for all counts (no threshold).** Rejected: small result sets would show
  approximate numbers where users notice and exactness is cheap to provide.
- **Extra `X-Total-Count` / `X-Total-Count-Estimated` HTTP header.** Rejected: the pagination
  signal already lives in the `JobSearchPage` body and is what the client-side query cache stores;
  splitting exactness metadata into a header would desync it from the cached body.
- **Materialised-view / trigger-maintained counters per filter.** Rejected: the filter space is
  effectively unbounded (free-text + multi-select combinations); impractical to precompute.
