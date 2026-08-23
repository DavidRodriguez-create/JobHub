# ADR 0020: Facet-response cache invalidated by a crawl-data generation stamp

- **Status:** Accepted
- **Date:** 2026-07-21
- **Deciders:** jobhub-architect (story #332 / ticket #399)
- **Affects:** job-service, db/init (index 049). No api-contracts change; no crawler-service change.

## Context

`GET /jobs/facets` (job-service) computes exclude-own-dimension aggregates for five dimensions
(`companies`, `locations`, `languages`, `employmentTypes`, `careerLevels`) plus a compensation
`min`/`max` range. Each dimension is a filtered `GROUP BY` / `COUNT` over `crawler.job_post`
(with the correlated location `EXISTS` and array-overlap predicates from ADR 0017), recomputed
on every filter change. The UI hits this endpoint on each keystroke/filter toggle, so a broad
listing recomputes six aggregate passes per interaction.

The underlying data is near-static between crawls: `crawler.job_post` only mutates when the
crawler writes. So the same filter combo yields the same facet payload until the crawler next
changes the table, which makes it a strong caching candidate, the same reasoning behind the
short-TTL count cache in ADR 0018 (`CountCache` / `CountCacheKey` / `CountCacheEntry`).

Constraints in scope:
- job-service is **Hexagonal**. The cache lives in `domain/service/` (like `CountCache`, an
  `@ApplicationScoped` domain service); the domain **model** stays framework-free; the DB read
  goes behind the existing `JobPostRepository` out-port.
- Contract-first: `GET /jobs/facets` response shape must not change (pure internal caching).
- Schema is owned by `db/init` SQL; Hibernate is `validate` in prod.
- No production code whose only purpose is testing: freshness/stamp logic takes `now` and the
  read stamp as parameters (the `CountCacheEntry` injected-`now` precedent).

The crux is **invalidation**: how job-service learns a crawl changed the data. Two candidate
signals were on the table (radar hint: "Touches: job-service, crawler-service"):
(a) job-service reads a cheap generation stamp from the `crawler` schema, TTL-guarded; or
(b) crawler pushes to a new internal `X-Service-Key` job-service endpoint on run completion.

A decisive finding from reading the crawler write paths: **facet-affecting data changes are not
all captured by any crawl-completion timestamp.** `applyEnrichment()`
(`crawler ... JobPostPanacheRepository`) fills exactly the facet dimensions (`employment_type`,
`career_level`, `languages`, `city`/`country`, `compensation_min`/`max`) in a **separate async
pass**, bumping `enriched_at` but **not** `last_seen_at` and **not**
`pull_target.last_successful_pull`. `normalizeLanguagesBatch()` (maintenance) rewrites the
`languages` facet dimension and bumps **no** timestamp at all. So `last_successful_pull`,
`pull_log.pulled_at` and `trigger_request.finished_at` all under-report facet freshness (they
track pulls/admin runs, not enrichment or maintenance), and "run completion" is not a single
well-defined moment to push on.

## Decision

We will add a **facet-response cache in job-service**, mirroring `CountCache`, invalidated by a
**generation stamp read cheaply from `crawler.job_post` and TTL-guarded** (option a). No
crawler-service change, no new HTTP endpoint, no contract change.

### Cache (domain/service, mirrors CountCache)

- `FacetCacheKey` (record) with `from(JobSearchQuery)` covering the **filter combo only**:
  `keyword, locations, languages, companies, employmentTypes, careerLevels, compensationMin,
  compensationMax, postedWithin`. `page`/`size`/`sort` are excluded (facets are a property of
  which postings match, not of how a page is sliced), identical rationale to `CountCacheKey`.
- `FacetCacheEntry` (record) `= (JobFacets value, Instant cachedAt, long generation)` with a pure
  `isFresh(Instant now, Duration ttl)` (injected `now`, no clock seam).
- `FacetCache` (`@ApplicationScoped`) bounded LRU via access-ordered `LinkedHashMap` +
  `removeEldestEntry`, `Collections.synchronizedMap`, config-driven enable/ttl/max-size, exactly
  like `CountCache`. `get(FacetCacheKey key, long generation)` returns the value only when an
  entry exists **and** `entry.generation == generation` **and** it is fresh; `put(key, value,
  generation)` stamps the current generation.

### Generation stamp (the invalidation signal)

- New out-port method on `JobPostRepository`, e.g. `long facetDataVersion()`, implemented in
  `JobPostPanacheRepository` as a single read:
  `SELECT COALESCE(EXTRACT(EPOCH FROM GREATEST(MAX(last_seen_at), MAX(enriched_at))) * 1000, 0)`
  `FROM crawler.job_post` (empty table to `0`). This reads directly from the data the facets are
  computed from, so it captures **inserts + re-crawls** (`last_seen_at`) **and enrichment**
  (`enriched_at`), which a pull/trigger timestamp would miss.
- A `CrawlGenerationStamp` (`@ApplicationScoped`, domain/service) TTL-guards the read: it caches
  the last stamp for `job.search.facets.stamp.ttl` and only re-queries the DB when that window
  elapses, so facet traffic never becomes a per-request `MAX` storm (the "cheap version-stamp
  read, TTL-guarded" precedent from ADR 0018 / #331/#383). The "should I re-read?" test is a pure
  function of `(lastReadInstant, now, ttl)` so it is unit-testable without a real clock. On a DB
  read error it returns the **last-known** stamp (fail-soft: serve within the entry TTL rather
  than fail the endpoint).

### JobService.getFacets wiring

Read `generation = crawlGenerationStamp.current()`, build `FacetCacheKey.from(query)`, return a
`FacetCache.get(key, generation)` hit if present, else compute `jobPostRepository.facets(query)`,
`put(key, result, generation)`, return it. When the stamp changes, all prior-generation entries
stop matching and age out by LRU; the entry TTL is the second bound (below).

### Config keys (job.search.facets.* prefix)

| Key | Default | Meaning |
|---|---|---|
| `job.search.facets.cache.enabled` | `true` | Toggle the facet cache. |
| `job.search.facets.cache.ttl` | `PT60S` | Per-entry safety-net TTL (see Consequences: covers stamp-blind mutations). |
| `job.search.facets.cache.max-size` | `500` | Max distinct filter-combos cached (LRU); facet payloads are larger than counts, so a smaller cap than count's 1000. |
| `job.search.facets.stamp.ttl` | `PT10S` | How often the crawl generation stamp is re-read; also the post-crawl staleness window before invalidation is observed. |

### Migration

`db/init/049-job-post-facet-stamp-index.sql`: two indexes on `crawler.job_post` to make the
stamp `MAX` an index backward-scan instead of a table aggregate:
`idx_job_post_last_seen_at (last_seen_at DESC)` and
`idx_job_post_enriched_at (enriched_at DESC)`. Pure DDL, authored inside the job-service ticket;
no new grant (`job_user` already holds `SELECT` on `crawler.job_post`), no crawler code.

## Consequences

- Positive: repeated/keystroke facet requests for a filter combo are served from memory until the
  crawler next changes the data; the six aggregate passes collapse to one compute per (filter
  combo x generation).
- Positive: **single service, one developer ticket.** No crawler change, no `X-Service-Key`/`.env`
  wiring, no new cross-service coupling, and it is correct under horizontal scaling (every
  job-service instance reads the stamp independently; a push would only invalidate one instance).
- Positive: stamp reads `job_post` directly, so enrichment (the async pass that fills the facet
  dimensions) invalidates correctly, which a `last_successful_pull` / trigger-completion signal
  would not.
- Negative / cost: bounded staleness window of `stamp.ttl` (default 10s) between a crawl write and
  the cache noticing, acceptable for filter-count chips.
- Negative / cost: the `MAX`-timestamp stamp cannot see facet-affecting mutations that bump no
  timestamp (e.g. `normalizeLanguagesBatch` maintenance, or a future hard-delete/prune). The
  per-entry `cache.ttl` (default 60s) is the deliberate backstop that expires such stale entries;
  this is why the design keeps both a generation stamp (fast common-path invalidation) and an
  entry TTL (worst-case bound), not one or the other.
- Negative / cost: a new config surface, one bounded in-memory cache, one new out-port method, and
  one DDL migration to maintain in job-service.
- Follow-ups: backend #NNN implements `FacetCacheKey`/`FacetCacheEntry`/`FacetCache` +
  `CrawlGenerationStamp` + the `JobPostRepository.facetDataVersion()` port method + migration 049,
  and wires `JobService.getFacets`. QAE covers: cache hit for a repeated filter combo, miss on a
  changed filter field, invalidation when the stamp advances (seed a new `last_seen_at`/
  `enriched_at`), and the `enabled=false` bypass. No frontend or contract work.

## Alternatives considered

- **HTTP push from crawler to an internal `X-Service-Key` job-service endpoint (option b).**
  Rejected: touches two services plus `.env`/compose key wiring; creates a new crawler to
  job-service dependency (reversing today's job reads crawler-DB direction); has no single "run
  completion" moment to fire on because enrichment and maintenance are separate async passes, so
  it would need multiple push points and still miss timestamp-less maintenance writes; and it only
  invalidates the one instance it hits, so it is less correct under horizontal scaling than a
  per-instance DB stamp read. Zero-staleness is its only edge, and a 10s stamp TTL makes that
  moot for filter chips. The radar's "Touches: crawler-service" hint is not binding: the
  enrichment analysis makes the single-service DB read strictly more correct.
- **Stamp from `pull_target.last_successful_pull` / `pull_log.pulled_at` / `trigger_request.finished_at`.**
  Rejected: all track pulls or admin-triggered runs, none capture the async enrichment pass that
  fills the facet dimensions, so facets would stay stale while enrichment backfills
  `employment_type`/`career_level`/`languages`/location/compensation.
- **Fold the stamp into the cache key (instead of a per-entry generation check).** Equivalent
  correctness, rejected on hygiene: on every stamp change the whole keyspace turns to dead entries
  that only LRU reclaims; a per-entry `generation` field lets a `get` reject stale entries
  explicitly and keeps the key a pure function of the filter combo (matching `CountCacheKey`).
- **Materialise facets per filter combo (trigger/table-maintained).** Rejected as in ADR 0018:
  the free-text + multi-select filter space is effectively unbounded; impractical to precompute.
