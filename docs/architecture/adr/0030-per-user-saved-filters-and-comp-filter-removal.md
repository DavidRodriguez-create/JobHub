# ADR 0030: Saved search filters are per-user server state, and the compensation filter leaves the UI

- **Status:** Accepted
- **Date:** 2026-08-07
- **Deciders:** jobhub-architect (story #523, ticket #525)
- **Affects:** JobHub-ui (`src/screens/JobSearch.jsx`, `src/api/mappers.js`, `src/components/FilterComponents.jsx`). Read-only for api-contracts and job-service.
- **Schema impact:** none. No `db/init` migration, no migration range assigned.

## Context

Story #523 reports two defects on the job-search screen.

1. **Saved filter presets are shared across users.** `JobSearchScreen` persists presets in a
   single global `localStorage` key, `jobhub_saved_filters`
   (`JobHub-ui/src/screens/JobSearch.jsx`, currently line 394). The key is browser-scoped, not
   user-scoped, so every account that logs in on the same browser sees, applies and deletes the
   same five presets. The reporter's words: "the filters seems to be shared for everyone? NOOOO,
   it should be per user".
2. **The compensation range filter is not wanted in the UI.** Crawled postings very often carry no
   salary at all, so a range slider that silently drops unpriced postings is a bad default control.
   The reporter asked for the control to go, and stated explicitly that the backend parameters
   should stay: "as it's flexible it's ok to maintain for the future".

The relevant constraint is that this is **not** a backend gap. job-service already ships the
correct, user-scoped surface:

- `GET/POST /jobs/filters/saved` and `PATCH/DELETE /jobs/filters/saved/{id}` in
  `api-contracts/src/main/resources/openapi/job-service.yaml` (schemas `SavedFilterRequest`,
  `SavedFilterPatchRequest`, `SavedFilterResponse`, `FilterValues`).
- `JobResource` guards all four operations with `@RolesAllowed("user")` and derives the owner from
  the Bearer token via `userId()`, never from a request field.
- `SavedFilterService` enforces the 5-preset ceiling per user; `SavedFilterPanacheRepository`
  scopes every read, update and delete by `userId` (`listByUser`, `countByUser`,
  `findByIdAndUser`).
- `crawler.saved_filter` already exists with `user_id UUID NOT NULL` and
  `idx_saved_filter_user` (`db/init/010-crawler.sql`), granted to `job_user`.
- `JobHub-ui/src/api/jobs.js` already exposes `listSavedFilters`, `createSavedFilter`,
  `updateSavedFilter`, `deleteSavedFilter` against those endpoints, with `auth: true`.

Everything exists except the wiring: the screen never calls the client. This ADR records the two
rulings that are durable beyond the story, because both have consequences the next change must
respect (user data location, and an intentionally unused backend capability).

## Decision

**Decision 1. The API is the single source of truth for saved filter presets when the user is
authenticated and `USE_API` is true.** `JobSearchScreen` loads presets with `listSavedFilters()`
on mount and on every `authed` transition, and mutates them exclusively through
`createSavedFilter` / `deleteSavedFilter`. React state is a render cache of the server list, never
a second store.

**Decision 2. The legacy `localStorage` key `jobhub_saved_filters` is dropped, not migrated, and
is actively removed on first mount.** Migrating it would take presets that may belong to a
*different* person who used this browser and attribute them to whoever logs in next, which is
exactly the privacy defect being fixed. There is no per-user attribution stored alongside the
legacy payload, so a correct migration is not even possible. The one-time
`localStorage.removeItem("jobhub_saved_filters")` is a cleanup of leaked shared state, not a
feature.

**Decision 3. Presets are an authenticated-only feature.** Anonymous visitors see neither the
"Saved filters" dropdown nor the "Save filter" button (the existing `authed` gate stays, and is now
the only gate). No anonymous fallback store is introduced, in any form.

**Decision 4. In mock/demo mode (`USE_API=false`) presets live in component state for the session
only.** The controls stay functional so the demo keeps its shape, and everything resets on reload.
No browser-persistent store is used, because mock mode has a fabricated account with no identity
to scope by, and reintroducing any shared browser bucket would reintroduce the bug.

**Decision 5. The compensation range control leaves the UI; the contract and the backend do not
change.** `GET /jobs` and `GET /jobs/facets` keep `compensationMin`/`compensationMax`,
`FilterValues` keeps both fields, `JobFacets` keeps its comp bounds, and
`buildSearchParams`/`buildFacetsParams` in `src/api/jobs.js` keep their pass-through support. The
screen simply stops passing them. The contract is **frozen unchanged** for this story: zero diff in
`api-contracts`.

**Decision 6. Compensation as *displayed data* and salary as a *sort* both survive.** The comp
string on `JobRow` and in the job detail drawer, and the "Salary: high to low" option
(`SORT_MAP.salary` to `salary-desc`, backed by `SortOption.SALARY_DESC` in
`JobPostPanacheRepository`) are unaffected. Only the range *filter* is removed. Sorting never hides
a posting; filtering does, and that was the objection.

**Decision 7. Presets persist filter dimensions only: no `sort`, no compensation.** The UI writes
a `FilterValues` body with `keyword`, `company`, `location`, `employmentType`, `careerLevel`,
`language`, `postedWithin`. It ignores `compensationMin`, `compensationMax` and `sort` when reading
a preset back, so any row written by an out-of-band client stays harmless.

## Consequences

- Positive: presets follow the account across browsers and devices, and are invisible to any other
  account on the same machine. The bug class disappears at the storage layer rather than being
  patched with a user-suffixed key.
- Positive: a backend capability that already existed, was tested and was dead is now exercised;
  no new endpoint, no new schema, no new migration.
- Negative: presets now require a network round-trip and can fail. The screen grows explicit
  loading and failure handling that a synchronous `localStorage` read did not need.
- Negative: anyone who had presets in the old key loses them, silently and irreversibly. Accepted:
  the ceiling is 5 named filters, they are cheap to recreate, and the alternative leaks data
  between accounts.
- Cost: `FilterValues.compensationMin/Max` and `PATCH /jobs/filters/saved/{id}` become contract
  surface the UI does not use. That is deliberate (the reporter asked for the flexibility to be
  kept) and must not be read as dead code to prune.
- Follow-ups: renaming a preset (the `PATCH` operation) has no UI affordance and is out of scope
  for #523. `SavedSettings.jsx`'s static "Hide jobs without compensation" toggle is unrelated mock
  chrome and is not touched here.

## Alternatives considered

- **Per-user `localStorage` key (`jobhub_saved_filters:<userId>`).** Rejected: it keeps user data
  in a store any other tab or account can enumerate, it does not survive a device change, and it
  duplicates a server feature that already exists and is already user-scoped and tested.
- **Migrate the legacy key into the API on first authenticated load.** Rejected: unattributable
  data, see Decision 2. It would also have to reconcile against the 5-preset ceiling and strip comp
  fields, all for presets nobody can prove belong to the person logging in.
- **Read-only fallback (show legacy presets until the first server write).** Rejected: it keeps the
  reported symptom alive (another account's presets rendered under your session) for an unbounded
  period, in exchange for a small convenience.
- **Remove `compensationMin`/`compensationMax` from the contract too.** Rejected explicitly by the
  reporter. Removing them would also break the facet comp bounds and the salary sort's data source
  for no gain.
- **Keep the slider but only apply it when the posting has a salary.** Rejected: it makes the
  control's effect depend on invisible data quality, which is harder to reason about than not
  having the control.
