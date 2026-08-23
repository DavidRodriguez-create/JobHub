# ADR 0001: Filter-aware (drill-down) facets for GET /jobs/facets

- **Status:** Accepted
- **Date:** 2026-06-06
- **Deciders:** Principal Architect (jobhub-architect); David R H
- **Affects:** api-contracts (`job-service.yaml`), job-service. No DB change.

## Context

`GET /jobs/facets` feeds the job-search filter controls (companies, locations, languages,
employmentTypes, careerLevels, and a compensationMin/Max range). Today every count is
**table-wide**: `JobPostPanacheRepository.facets()` runs one `GROUP BY` per dimension over the
whole table with no `WHERE` clause, and the `JobFacets` domain record documents counts as
"table-wide and independent of any active filter". The UI therefore shows the same numbers no
matter what the user has selected — there is no signal about how a selection narrows the data.

Story #4 (sub-issue #11) makes the facets **reactive** to the active filters (drill-down):
selecting `location=France` should make the company/language/employment-type/career-level
counts reflect France only. The constraint that makes this non-trivial is that a user must
still be able to **multi-select within a dimension**: after picking France, the `locations`
group must keep listing Spain, Germany, … so they can be added. A naïve "apply all filters to
every facet" would collapse each chosen dimension to the single selected value.

Conventions in scope: job-service is **Hexagonal** (`CLAUDE.md` decision guide — technical
service, REST/persistence are the complexity); the contract in
`api-contracts/.../openapi/job-service.yaml` is the single source of truth (interface-only
generation); dynamic JPQL is built only via the repository's `buildQuery()`/`appendFilters()`
helper with bound parameters (no string-concatenation of user values); schema is owned by
`db/init` SQL and Hibernate is `validate` in prod. The endpoint is **read-only**.

## Decision

We will make `GET /jobs/facets` **filter-aware using an "exclude own dimension" rule**, freeze
that contract now, and implement it in job-service within the existing Hexagonal layering.

**Contract (frozen in this change).** `GET /jobs/facets` accepts the same optional filter
query parameters as `GET /jobs` — `keyword`, `location[]`, `language[]`, `company[]`,
`employmentType[]`, `careerLevel[]`, `compensationMin`, `compensationMax`, `postedWithin` —
with identical names, types, `style/explode`, and enums. It does **not** accept `sort`, `page`
or `size` (they do not affect aggregate counts). The response schema (`JobFacets` /
`FacetValue`) is **unchanged in shape**; only its prose is updated so it no longer claims the
counts are table-wide. The operation stays `x-implementation-status: new` (it is not yet built
in `JobResource`). The change is **backward-compatible**: all params are optional, and omitting
them all reproduces the original table-wide behaviour.

**The exclude-own-dimension rule.** Each facet group is computed against **all active filters
except that group's own dimension**:

| Facet group | Filters applied | Filter deliberately excluded |
|---|---|---|
| `companies` | all except `company` | `company` |
| `locations` | all except `location` | `location` |
| `languages` | all except `language` | `language` |
| `employmentTypes` | all except `employmentType` | `employmentType` |
| `careerLevels` | all except `careerLevel` | `careerLevel` |
| `compensationMin` / `compensationMax` range | all except `compensationMin` **and** `compensationMax` | both comp bounds |

`keyword` and `postedWithin` are **not facet groups** of their own (no control lists their
distinct values), so they are applied to **every** group. They only ever narrow the data and
never need to be "re-widened" by re-selection, so there is no reason to exclude them anywhere.

This is precisely what preserves multi-select: within a dimension, that dimension's own filter
is off, so the user still sees its other values (with counts reflecting the *other* active
filters) and can add them; across dimensions, the selection narrows the counts as expected.

**Compensation-range decision.** The returned `compensationMin`/`compensationMax` bound the
postings matching every active filter **except the comp filter itself** — the range excludes
its own dimension, exactly like the categorical groups. Rationale: the range drives a
min/max slider; if it were narrowed by the user's own comp selection the slider would collapse
to the chosen sub-range and the user could never widen it again. Excluding both comp bounds
keeps the slider's outer rails stable (reacting only to the *other* filters) while the chosen
sub-range lives in the request, not the response. Each bound stays `nullable` and is `null`
when no in-scope posting carries that compensation field.

**Zero-count values.** A value whose count is 0 under the in-scope filters is omitted from its
group (it falls out of the `GROUP BY` naturally). The UI keeps the user's already-selected
values from request state, so an excluded-but-selected value does not disappear from the
control.

**Recommended backend implementation (for #14).** Reuse the existing dynamic-JPQL machinery.
Concretely:

1. Carry the active filters into the port. `JobFacets facets()` becomes
   `JobFacets facets(JobSearchQuery query)` (sort/page/size are simply ignored by the facet
   path). `GetJobFacetsUseCase.getFacets()` becomes `getFacets(JobSearchQuery query)`. The
   `JobResource.getJobFacets(...)` REST method builds a `JobSearchQuery` from the new params
   exactly as `searchJobs(...)` already does (reuse the same `parseEmploymentTypes` /
   `parseCareerLevels` / `parsePostedWithin` helpers; bad enum values → 400, hence the new
   `400` response in the contract).
2. Refactor the monolithic `appendFilters(jpql, params, query)` into per-clause appenders
   (one private method per dimension: keyword, location, company, employmentType, careerLevel,
   compensation, postedWithin, language) **without changing their SQL or binding** — they must
   stay parameter-bound. Add an `appendFiltersExcept(jpql, params, query, Dimension excluded)`
   that composes every clause whose dimension is not `excluded`. This keeps the rule in one
   place and guarantees the facet `WHERE` clause is identical to search for shared filters.
3. Compute facets as **one aggregation query per dimension**, each calling
   `appendFiltersExcept(...)` with its own dimension excluded, then `GROUP BY` that dimension
   (the comp range is a single `MIN(...)/MAX(...)` query excluding both comp bounds). The
   `locations` "Remote" synthetic bucket and the `languages` native `unnest` query keep their
   current shapes; they just gain the composed `WHERE` (the native languages query must be
   parameterised the same way — still no user-value concatenation).

**Why per-dimension queries, not one grouping query.** A single query cannot express
"exclude a *different* filter per group" — each group needs a different `WHERE`. The cost is up
to 7 small aggregate queries per request (≈ the 7 the endpoint already issues today, now each
with a `WHERE`), all on indexed columns over a modest table, behind one read-only request.
That is an acceptable, well-understood trade-off and keeps the SQL legible. If profiling ever
shows this hot, the follow-up is a single multi-grouping query (e.g. `GROUPING SETS` / per-row
`FILTER`), recorded as a future ADR — not premature here.

## Consequences

- Positive: filter controls become reactive (drill-down) while multi-select within a dimension
  still works; the contract stays backward-compatible (table-wide remains the no-filter case);
  the facet `WHERE` reuses the same parameter-bound clauses as `/jobs`, so search and facets
  cannot drift; no DB migration (read-only) — **migration range for #14 is N/A**.
- Negative / cost: the port and use-case signatures change (`facets()` →
  `facets(JobSearchQuery)`), so #14 must update `JobPostRepository`, `GetJobFacetsUseCase`,
  `JobFacetsService`, `JobResource`, and the regenerated `JobsApi` together — the regenerated
  interface will not compile against the current no-arg `JobResource.getJobFacets()` until then
  (expected at a contract-freeze gate). Up to ~7 aggregate queries per request instead of a
  single page of work; the per-dimension exclusion rule must be unit-tested per group.
- Follow-ups: implement #14 per the recommended approach; update the `JobFacets` domain-record
  javadoc to drop "table-wide" (mirror the contract prose); unit-test each group's exclusion +
  the comp-range exclusion + the no-filter == table-wide invariant; component-test the endpoint
  (DevServices, real Postgres, fixed seed). The build-out plan tracks the ordered steps.

## Alternatives considered

- **Apply ALL active filters to every facet group (including its own).** Rejected: it
  collapses each selected dimension to the chosen value(s), so the user can never widen or
  multi-select within a dimension — it breaks the core UX this story exists to enable.
- **Single grouping query for all dimensions.** Rejected for now: it cannot apply a *different*
  exclusion per group in one statement, and the constructs that approximate it (`GROUPING
  SETS`, per-aggregate `FILTER`) are markedly less readable for a marginal win on a small
  table. Kept as a documented future optimisation behind profiling.
- **Compensation range narrowed by the active comp filter (include own dimension for comp
  only).** Rejected: it collapses the slider rails to the user's own selection so they can
  never widen the range again — inconsistent with how every categorical group excludes its own
  dimension. Excluding both comp bounds keeps the slider's outer rails stable.
- **A new parallel endpoint (e.g. `/jobs/facets/filtered`).** Rejected: it duplicates the
  surface and splits the UI's data source; optional params on the existing operation are
  backward-compatible and keep one source of truth.
