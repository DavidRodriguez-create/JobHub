# ADR 0017: Multiple locations per job post

- **Status:** Accepted
- **Date:** 2026-07-05
- **Deciders:** jobhub-architect (David R H)
- **Affects:** crawler-service, job-service, api-contracts, db/init, JobHub-ui

## Context

Story #1 (sub-issues #291 crawler, #292 job-service): a single job post can have
openings in several countries/cities, and the product must store and show all countries
related to an opening.

Today `crawler.job_post` carries exactly one location as two flat columns, `city TEXT`
and `country TEXT` (see `db/init/010-crawler.sql`). crawler-service owns that schema;
job-service reads it cross-schema and exposes:

- a single `location` string per posting, derived in the domain model as
  `city + ", " + country` (or the non-blank part alone);
- a repeatable `location` filter (`appendLocation` in `JobPostPanacheRepository`) that
  splits each value on comma — `city,country` matches both columns, a single token
  matches `city OR country`, and `"Remote"` matches `LOWER(city)='remote' OR
  LOWER(country)='remote'`;
- a `locations` facet (`locationFacets`) that groups by `country` (non-remote) and adds a
  synthetic `"Remote"` bucket.

There is no "remote" flag; `"Remote"` is a magic value stored in `city`/`country`.

Constraints:

- **Backward compatibility is mandatory.** The single `city`/`country` columns, the
  single-`location` API string, the `content_hash` (which hashes `city`), and the 017
  performance indexes on `LOWER(city)`/`LOWER(country)`/`(country,city)` must keep working.
- Contract-first: the API surface changes in `api-contracts` first (interface-only gen),
  additively.
- The database is owned by numbered `db/init` SQL; schema-per-service; `crawler_user` owns
  the crawler schema, `job_user` gets least-privilege SELECT.
- Both services are Hexagonal.

## Decision

**We will add a one-to-many child table `crawler.job_post_location` and keep the existing
`crawler.job_post.city`/`country` columns as the PRIMARY location.**

Data model (see `db/init/014-crawler-job-post-location.sql`):

- `job_post_location(id, job_post_id FK ON DELETE CASCADE, country, city, is_primary,
  position, created_at)`.
- `(country, city)` carry the same semantics as the parent columns: either part may be
  NULL; `"Remote"` may live in either; no separate flag.
- Exactly one row per post has `is_primary = TRUE` (partial unique index). The primary row
  **mirrors** `job_post.city`/`country`, so the child table alone is a complete picture of
  every opening, primary included.
- `UNIQUE NULLS NOT DISTINCT (job_post_id, country, city)` dedupes openings per post.
- The migration backfills one `is_primary` row per existing post from its current
  `city`/`country`, and grants `job_user` SELECT.

`job_post.city`/`country` remain the source of truth for the primary location, the
`content_hash`, and the single-`location` string. crawler-service writes both the parent
columns (primary) and the full child set; when it only knows one location, it writes one
primary child row and the picture is unchanged.

### Contract (api-contracts / `job-service.yaml`, all additive, `x-implementation-status: planned`)

- `JobPostResponse.location` (string) is unchanged: the **primary** opening.
- New `JobPostResponse.locations`: array of new `JobLocation { country?, city?, primary }`,
  primary first. A single-opening post returns one entry equal to `location`.
- `location` filter (on `GET /jobs` and `GET /jobs/facets`): a post matches when **any** of
  its openings matches **any** supplied value; each matching post is returned once
  (dedup by post id). Implemented as an `EXISTS` over `job_post_location` OR-ed with the
  existing primary-column predicate (so it still works before/without child rows).
- `locations` facet: distinct countries across **all** of each post's openings, counting a
  post once per distinct country (duplicate same-country openings collapse), plus the
  `"Remote"` bucket. Sums across countries may exceed the post count — documented in the
  schema. The drill-down "exclude own dimension" semantics (ADR 0001) are preserved.

### Scope: crawler population is a follow-up

**This story delivers storage + API + UI surface. crawler-service does NOT yet populate
multiple openings from job-board sources.** #291 delivers the table, the backfill (one
primary row per post), and writes the primary row on new posts; it does not add
multi-location extraction. Rationale: most sources give one location per posting, so the
genuine multi-location signal comes from **dedup/merge of the same posting across country
sites** (or an enrichment pass), which is a separate, larger change. Freezing the storage
and contract now unblocks job-service (#292) and the UI immediately, with real multi-value
population added later behind the already-frozen shape (a follow-up ticket).

## Consequences

- Positive: fully backward compatible — existing columns, string, hash, indexes, filter
  and facet all keep working; the contract change is purely additive.
- Positive: the child table is the single complete source of openings (primary mirrored in),
  so response mapping and facets read one place.
- Positive: filter/facet extend by OR-ing an `EXISTS`/JOIN onto the current predicates, so
  they degrade gracefully to today's behaviour when a post has only the primary row.
- Negative / cost: crawler-service must now keep the primary child row in sync with
  `job_post.city`/`country` on write (two writes in one transaction).
- Negative / cost: the `locations` facet and `location` filter gain a join/unnest;
  mitigated by `idx_job_post_location_lower_country` / `_lower_city`. Facet cost is the main
  risk — see Risks in the handoff; validate the query plan against seed data.
- Negative / cost: per-country facet counts must `COUNT(DISTINCT post)` to avoid
  double-counting a post with two openings in the same country.
- Follow-ups: a later ticket adds real multi-location population (dedup/merge or enrichment);
  QAE must cover duplicate-country dedup, the Remote bucket with child rows, and
  filter-returns-post-once.

## Alternatives considered

- **`country`/`city` as `TEXT[]` arrays on `job_post`** — rejected: loses the per-opening
  pairing (which city goes with which country), complicates the primary/`content_hash`
  story, and the `city,country` filter form and per-country facet get awkward. A child
  table pairs cleanly and reuses the 017 `LOWER()` index pattern.
- **JSONB column of openings** — rejected: no clean per-country GROUP BY for the facet,
  weaker indexing for the case-insensitive match, and no FK/dedup guarantees.
- **Replace `city`/`country` with the child table (no primary columns)** — rejected:
  breaks backward compatibility (single-`location` string, `content_hash`, 017 indexes) and
  forces a bigger, riskier migration for no near-term benefit.
- **Populate multi-location from sources now** — deferred, not rejected: most sources are
  single-location; the real signal is cross-country dedup/merge, a separate change. Freezing
  storage + contract now is the minimal, additive step.

## Update (2026-07-11): Story #319 realises single-source multi-opening population

Story #305 shipped only the plumbing frozen above: the child table, the backfill, the
job-service filter/facet, and the UI surface, with `crawler.additionalLocations` always empty
and the contract fields `JobPostResponse.locations` + `JobLocation` held at
`x-implementation-status: planned`. Story #319 (tickets #323 crawler, #324 job) makes
multi-location real end-to-end and flips those two fields to `existing`. This section records
the scope boundary the deferral above left open.

### Scope decision: single-source multi-opening, NOT cross-source dedup

We deliver **single-source multi-opening extraction**: one posting that, in the source
client's own list payload, already enumerates several offices/locations. We explicitly do
**NOT** deliver **cross-source / cross-country dedup-merge** (recognising that the "same"
posting fetched under two country facets, or from two boards, is one logical job with several
openings). Cross-source merge needs a stable cross-posting identity key and a merge pass that
does not exist today; it stays a separate, larger follow-up. Single-source multi-opening needs
no identity key: the openings are already grouped under one posting by the source itself.

### The boundary: where the extra openings come from

The additional openings are read in the **source client parse step**
(`adapter/out/client/source/*`, e.g. Lever `parseJobs`), from an array field already present
in the API response the client fetches. No new HTTP calls, no enrichment/LLM pass, no new
pipeline stage.

- **Primary source (reliable): Lever `categories.allLocations[]`.** The Lever v0 postings
  payload carries `categories.location` (the single canonical location string, e.g.
  `"Barcelona, Spain"`) plus `categories.allLocations`, an array of every location the posting
  is open in. `categories.location` stays the PRIMARY opening, but it is now comma-split into
  `(city, country)` (see the Ruling below) instead of being stored raw in `city` with `country`
  null. The remaining `allLocations` entries are comma-split the same way and mapped into
  `JobPost.additionalLocations`. This is the honest slice that reliably yields >1 real opening.
- **Secondary candidate (verify fixtures first): Greenhouse `offices[]`.** Greenhouse jobs can
  carry an `offices` array alongside the single `location.name`. It MAY be geographic or MAY be
  org-structure; the developer should confirm against a real captured fixture before mapping it,
  and skip it if the office names are not locations. Not required for the story to be "done".
- **Not viable in this slice:** Workday `locationsText` is a summarised string ("2 Locations"),
  not enumerable from the list endpoint; SmartRecruiters and Amazon return one `{city,country}`
  per posting row by API shape (Amazon sweeps configured locations, but each result row is a
  single place). These stay single-opening until a detail-fetch or merge pass is built.

### Ruling (2026-07-11): the primary opening is comma-split, hash stays on the raw string

A shape-gate question surfaced during build: `LeverJobSourceClient` today stores
`city = categories.location` (the whole raw string) with `country` null, and feeds that same
raw string into `JobPost.computeHash`. The ruling for ticket #323, verbatim:

1. **Primary opening: comma-split it.** Parse `categories.location` into `(city, country)` and
   store the split values in the primary `city`/`country` (domain `JobPost.city`/`country`), NOT
   the raw string with a null country. Reuse ONE parse helper: promote Greenhouse's
   `parseCity`/`parseCountry` into a shared `adapter/out/client/support` helper (e.g.
   `LocationParser`) and call it from Lever, Greenhouse, and the additional-locations mapping so
   all three split identically. Rationale: `syncLocations` mirrors the primary opening into an
   `is_primary` child row from `domain.city`/`country`; if the primary stayed an unsplit string
   with a null country, the PRIMARY opening would carry no country and would be invisible to the
   country facet and the country `location` filter, defeating the story for the main opening.

2. **`computeHash` stays fed by the raw `categories.location` string.** Confirmed: the column
   split does NOT re-key any posting, because the hash input is the raw location string (the 3rd
   `computeHash` argument), not the `city` column value. Keep it exactly as today. Seed/behaviour
   impact: on a fresh dev/test volume every Lever row is inserted with the split primary, so
   component seeds and QAE cases must expect `(city="Barcelona", country="Spain")`, not
   `(city="Barcelona, Spain", country=null)`. Existing prod rows keep their old parent columns
   until re-crawled (the hash is unchanged, so re-crawl hits the update path): see point 4.
   Country faceting/filtering of those pre-existing rows is still correct because the freshly
   written primary child row carries the split country regardless.

3. **`additionalLocations`: comma-split each entry, deduped against the primary.** Map every
   `categories.allLocations[]` entry through the same `LocationParser` into a
   `JobPostLocation(city, country, primary=false)`. As shipped, `LeverJobSourceClient` performs a
   narrow, case-insensitive exclusion of the entry that matches the primary at parse time (so
   `JobPost.additionalLocations` off `parseJobs()` never carries the primary as a duplicate,
   which the pre-persistence test cases TC-319-CRAWL-01/03/04 assert). The authoritative n-way
   `(country, city)` dedup remains `JobPostMapper.toLocationEntities` (case-insensitive, primary
   first) as the downstream safety net; the client does not re-implement it, it only excludes the
   primary match.

4. **Preserve the parent to primary-child mirror on update.** `updateEntity` currently rewrites
   only title/url/description/lastSeenAt, so a re-crawled existing Lever row would keep its stale
   unsplit parent `city`/`country` while `syncLocations` writes a freshly-split primary child
   row, breaking ADR 0017's "primary child mirrors the parent columns" invariant. To keep the
   invariant true and let existing rows self-heal, also re-sync `city`/`country` from the domain
   on the update path. This is safe and idempotent: the update path only runs on a hash match,
   which guarantees the raw location string (and therefore its deterministic split) is identical,
   and `updateEntity` does not recompute `content_hash`, so the stored hash is untouched.

### What does NOT change (reuse, do not rebuild)

- **Persistence is already complete.** `JobPostMapper.toLocationEntities` builds one child row
  per DISTINCT `(country, city)` opening from `domain.locations()`, primary first (position 0),
  deduping case-insensitively and keeping the primary when a duplicate appears;
  `JobPostPanacheRepository.syncLocations` delete-then-reinserts the full child set in the parent
  write transaction. Beyond the primary split and the update-path re-sync (Ruling above),
  populating `additionalLocations` in the client is the crawler change needed for storage; the
  write path already fans the full opening set out to N child rows.
- **`content_hash` and the single-`location` string are unchanged; `job_post.city`/`country` now
  hold the SPLIT primary.** `computeHash` keeps being fed the raw `categories.location` string
  (not the split `city`), so no posting is re-keyed and update-in-place dedup and the 017 indexes
  are unaffected. The primary `city`/`country` columns move from raw-string/null to
  `(city, country)` (Ruling above); job-service's derived single-`location` string recomposes to
  the same display value (`"Barcelona, Spain"`), so the response is unchanged.
- **job-service needs no filter/facet code change.** The `location` filter already OR-s the
  primary-column predicate with an `EXISTS` over `job_post_location` (correlated on `j.id`, not a
  JOIN, so a multi-opening post is returned once), and the `locations` facet already merges the
  primary-column and child-row `(post, country)` sets via a `Set` and counts `DISTINCT post` per
  country. #324 is verify/harden only: add a zero-location case, confirm the returned-once and
  duplicate-country-collapse behaviour against real multi-child seed data, and re-check the facet
  query plan (`EXPLAIN`) now that child rows are non-empty. No JPQL/SQL change is expected.

### Contract change (this update)

Additive and backward-compatible: `JobPostResponse.locations` and the `JobLocation` schema flip
from `x-implementation-status: planned` to `existing` in `api-contracts/.../job-service.yaml`.
The `location` filter and `locations` facet descriptions already state the primary-OR-child match
and DISTINCT-post-per-country counting semantics precisely and are left as frozen. The unrelated
`Trigger*` schemas are not touched.

### Migrations

**N/A for both #323 (crawler) and #324 (job).** Schema is out of scope: `crawler.job_post_location`
(table, `is_primary` partial-unique index, `(job_post_id, country, city)` dedup constraint,
backfill, `job_user` SELECT grant) already exists from `db/init/014-crawler-job-post-location.sql`,
and the `LOWER(country)`/`LOWER(city)` performance indexes from `017`. No new `db/init/NNN` file is
assigned to either ticket.

### Architecture

Both services stay **Hexagonal**. All crawler changes live in `adapter/out/client/source/*`
(a boundary adapter) and flow into the unchanged domain model (`JobPost.additionalLocations`) and
the unchanged persistence adapter. No domain, port, or layering change.

### Risks for the developers

- **Lever is the only source that reliably yields >1 real opening in this slice.** Targets that
  are not Lever, or Lever roles posted at a single office, will still write exactly one primary
  child row. QAE/component coverage for genuine multi-child behaviour must seed Lever-style data
  (or seed `job_post_location` directly); do not expect SmartRecruiters/Amazon/Workday fixtures to
  produce multiple openings.
- **Do not change what feeds `computeHash`.** Keep passing the raw `categories.location` string as
  the hash input, exactly as today. The primary column split (raw string to `(city, country)`)
  does NOT touch the hash input, so it does not re-key any posting (QAE reasoning confirmed).
  Hashing the split `city` instead is what would re-key every posting and break update-in-place
  dedup, so do not do that.
- **Greenhouse `offices[]` is unconfirmed.** Treat it as optional and fixture-gated; do not ship a
  mapping that turns org-structure office names into fake locations.
