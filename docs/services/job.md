# job-service

**Architecture:** [Hexagonal](../architecture/hexagonal.md) · **Schema:** `job` · **Port:** 8081 ·
**Routed at:** `/jobs`

## Responsibility

The read API over crawled job postings. Browse, search and filter postings; bookmark jobs per user;
and persist named search-filter presets. JWT **verify-only** (it trusts tokens signed by
auth-service).

## Endpoint groups

| Group | Paths | Purpose |
|-------|-------|---------|
| Browse & search | `/jobs`, `/jobs/{id}`, `/jobs/facets` | List/search postings; facets for filters |
| Saved jobs | `/jobs/saved`, `/jobs/saved/{jobId}` | Bookmark jobs per authenticated user |
| Saved filters | `/jobs/filters/saved`, `/jobs/filters/saved/{id}` | Named filter presets (max 5 per user) |

Pagination uses an `X-Total-Count` response header; numeric query params are bounded with
`@Min`/`@Max` and the config-derived `maxSize`.

See the full contract on the [API reference → job-service](../api/job.md) page.
