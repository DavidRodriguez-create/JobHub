# ADR 0005: Job-post query performance -- missing indexes and full-text search

- **Status:** Accepted
- **Date:** 2026-06-12
- **Deciders:** architect (design), developer (implementation)
- **Affects:** db/init (new 017-job-post-perf.sql), job-service (repository + entity), crawler-service (entity), podman-compose.yml (volume mount)

## Context

The `job-service` search/facet queries on `crawler.job_post` have two performance gaps:

1. **Missing indexes for expression-based filters.** Several WHERE clauses use `LOWER()` or
   array functions (`array_overlaps`) that cannot use the existing B-tree indexes. Every
   location, company, and language filter forces a sequential scan.

2. **LIKE-based keyword search.** The keyword filter (`LOWER(title) LIKE '%term%'`) forces a
   full table scan on every search and every facet query that carries a keyword. As the
   `job_post` table grows, this becomes the dominant cost.

JobHub conventions: schema changes are owned by numbered files under `db/init/`; Hibernate is
`validate` in prod and `drop-and-create` in dev/test; per-service least-privilege users;
`job_user` has SELECT-only on `crawler.job_post` and `crawler.pull_target`.

## Decision

### Tier 1: Missing indexes (db/init only, zero Java changes)

We will add five indexes in `db/init/017-job-post-perf.sql`:

| Index | Type | Target | Covers |
|---|---|---|---|
| `idx_job_post_languages_gin` | GIN | `job_post.languages` | `array_overlaps(jp.languages, ?)` |
| `idx_job_post_lower_city` | B-tree functional | `LOWER(city)` | `LOWER(j.city) = ?` |
| `idx_job_post_lower_country` | B-tree functional | `LOWER(country)` | `LOWER(j.country) = ?` |
| `idx_pull_target_lower_company` | B-tree functional | `LOWER(company_name)` on `pull_target` | `LOWER(t.companyName) IN ?` |
| `idx_job_post_comp_range` | B-tree composite | `(compensation_min, compensation_max)` | range filters on both columns |

All use `CREATE INDEX IF NOT EXISTS` for idempotent application.

### Tier 2: Full-text search (db/init + Java changes)

We will add a `search_vector tsvector` column to `crawler.job_post`, maintained by a
BEFORE INSERT/UPDATE trigger on `title`/`description`, with a GIN index. Existing rows are
backfilled in the same migration script.

**Dual-context problem:** `appendKeyword()` is called from both JPQL paths (`search`,
`count`, standard facets) and the native-SQL path (`languageFacets`). The `@@` operator
is PostgreSQL-native.

**Chosen approach:** use Hibernate 6's `sql()` function to embed the native `@@` operator
inside HQL. `appendKeyword` will emit:

```
AND sql('? @@ plainto_tsquery(''english'', ?)', j.searchVector, :keyword) = true
```

This works in the JPQL context because `sql()` passes the fragment through to the generated
SQL, and `j.searchVector` is resolved via the standard entity field mapping. For the native
SQL path in `languageFacets`, the existing alias-replacement map converts
`j.searchVector` to `jp.search_vector`, and the `sql()` wrapper is stripped/rewritten since
`languageFacets` already builds raw SQL -- the `appendKeyword` output is consumed as a string
fragment and the `sql(...)` call needs to be replaced with raw `jp.search_vector @@
plainto_tsquery('english', :keyword)` in the native context.

To handle both contexts cleanly, `appendKeyword` will emit the clause in JPQL form using
`sql()`, and `languageFacets` will add a replacement rule to convert the `sql()` wrapper to
its native equivalent.

## Consequences

- Positive: GIN on `languages` eliminates seq scans for language filters. Functional indexes
  on `LOWER()` columns serve location and company filters. Full-text search replaces
  `LIKE '%term%'` with an index-backed `tsvector @@ tsquery` lookup.
- Positive: the trigger keeps `search_vector` in sync -- no application-level maintenance.
- Negative / cost: `search_vector` adds ~200-500 bytes per row. Trigger adds small write cost.
- Negative / cost: `JobPostEntity` in both `job-service` and `crawler-service` must map the
  column for Hibernate `validate` in prod. The field is write-never from Java.
- Follow-ups: `podman-compose.yml` must mount `017-job-post-perf.sql`. Test init scripts need
  the trigger function for keyword-search component tests.

## Alternatives considered

- **pg_trgm GIN index on title/description** -- supports `LIKE '%term%'` without Java changes,
  but indexes are larger/slower to build and lack relevance ranking. Rejected: tsvector is the
  standard PostgreSQL full-text approach.
- **Hibernate Search (Lucene/Elasticsearch)** -- full-featured but adds infrastructure. Rejected:
  operational complexity disproportionate to single-field keyword search.
- **Convert all queries to native SQL** -- eliminates JPQL/native duality but is a large
  refactor touching every query method and losing Hibernate's entity mapping in `search()`.
  Rejected: `sql()` function keeps most code as HQL with a surgical native fragment.
- **`@Formula` on entity** -- evaluated on every SELECT, cannot be used in WHERE clauses.
  Rejected: does not solve the problem.
