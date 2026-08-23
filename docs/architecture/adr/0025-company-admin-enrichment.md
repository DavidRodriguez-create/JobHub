# ADR 0025: Admin company enrichment: record-level manual-edit override and the admin endpoints

- **Status:** Accepted
- **Date:** 2026-07-25
- **Deciders:** jobhub-architect (David R H)
- **Affects:** job-service (new admin read/list/update path, write path sets provenance),
  api-contracts (`job-service.yaml`: new `CompanyUpdateRequest` schema + three planned admin
  operations, `CompanyInfo` reused unchanged), JobHub-ui (new admin enrichment screen),
  db/init (no new migration, see D4), crawler-service (no change), application-service and
  notification-service (no change).

## Context

Story #430 (sub-issue #452), parent #426, US 4 of 5: "admin can enrich company information".
An admin browses stored companies, opens one, and edits its enrichable fields (`website`,
`industry`, `size`, `headquarters`, `description`, `tags`, `logoUrl`). A field an admin has
edited must win over any later crawl.

Binding constraints from the earlier stories, not reopened here:

- **ADR 0023 D1: job-service is the sole writer of `crawler.company`.** The table physically
  lives in the crawler schema but is owned, modelled and written by job-service.
- **ADR 0023 D4: `CompanyInfo` is frozen** with every field this story needs already present
  (`id`, `slug`, `website`, `industry`, `size`, `headquarters`, `description`, `tags`,
  `logoUrl`, `manuallyEdited`, `updatedAt`), and it explicitly records that per-field
  provenance stays internal to job-service while `manuallyEdited` is the only provenance a
  client sees. This story must NOT reopen `CompanyInfo`.
- **ADR 0023 D3 + the code:** the resolution reconciler (`CompanyResolutionService` to
  `CompanyPanacheRepository.upsertBySlug`) is INSERT-ONLY:
  `INSERT ... ON CONFLICT (slug) DO NOTHING` then re-select. It performs no `UPDATE` of an
  existing company row.
- **ADR 0024 D3: the manual-edit guard is structural.** The crawl/reconcile path cannot
  overwrite because it never updates; the one-time logo backfill (`052`) carries
  `WHERE logo_url IS NULL AND manually_edited = false`; and there is a standing rule that any
  future field-refresh `UPDATE` on an existing company MUST carry `WHERE manually_edited = false`.
- **The schema already supports it.** `db/init/051-job-company.sql` gives `crawler.company` all
  the enrichable columns plus `source VARCHAR(16)` (`CHECK IN ('crawl','derived','manual')`),
  `manually_edited BOOLEAN NOT NULL DEFAULT FALSE` and `updated_at`. `job_user` already holds
  `SELECT, INSERT, UPDATE ON crawler.company` (051). No `DELETE` anywhere by design.
- job-service is **Hexagonal** and stays so: this is a mechanistic REST + persistence
  extension, not invariant-heavy domain behaviour.

The open questions this ADR closes: (1) override granularity, per-record vs per-field; (2) the
shape of the admin endpoints and the update verb/semantics; (3) whether a migration is needed.

## Decision

### D1. Override granularity: per-record `manually_edited`. No per-field provenance.

We keep the existing single per-record `manually_edited` boolean as the override signal. We do
NOT add per-field provenance (no `text[]` of edited field names, no per-field boolean columns).

Rationale. Per-field provenance only earns its keep when a write path updates individual fields
of an existing company from a fresh source AND you still want admin-edited fields to win
field-by-field. No such path exists: the reconciler is insert-only (it never updates an existing
row), and ADR 0024 deferred the "reconciler UPDATEs existing companies to refresh" option. So
the ONLY writes that could ever clobber an admin edit are whole-row-guardable UPDATEs, for which
a record-level flag is exactly sufficient. Per-field storage would be speculative machinery for a
crawl path that does not exist, and it would contradict the already-frozen `CompanyInfo`
contract, which states per-field provenance stays internal and exposes only the record-level
`manuallyEdited`. This is also consistent with how ADR 0024 treated the logo: the logo backfill
guarded at the record level (`manually_edited = false`) plus a value guard (`logo_url IS NULL`);
the logo was never given its own provenance flag. Record-level is the same model, uniformly
applied.

When unsure, `CLAUDE.md` says prefer the simpler design and record why: this is that call.

### D2. Resolution-path rule per editable field (how the crawl path honours the override).

The rule is identical for all seven editable fields (`website`, `industry`, `size`,
`headquarters`, `description`, `tags`, `logoUrl`) because the override is record-level:

- **The reconciler (`CompanyResolutionService.resolvePending` to `upsertBySlug`) never updates an
  existing company row.** It only inserts on a brand-new slug. Therefore it cannot overwrite any
  admin-edited field of any existing company, for any field, by construction. This is the primary
  enforcement and it needs no per-field check.
- **Any future field-refresh `UPDATE` on an existing company MUST carry
  `WHERE manually_edited = false`** (the ADR 0024 D3 standing rule, restated). Because
  `manually_edited = true` pins the whole row, a single record-level predicate protects all seven
  fields at once. There is no field for which a different rule applies.
- **The admin update itself is the only path that sets the flag.** On a successful
  `PUT /jobs/admin/companies/{id}`, job-service sets `manually_edited = true`, `source = 'manual'`
  and `updated_at = now()` in the same statement that writes the edited fields. From that point
  the record is pinned against every crawl-side write above.

Net: "a field an admin edited wins over a later crawl" is satisfied because editing any field
pins the record, and no crawl path updates a pinned (or in fact any existing) record.

### D3. Admin endpoints: three operations, `admin` JWT group, reuse `CompanyInfo`.

Frozen in `api-contracts/src/main/resources/openapi/job-service.yaml`, all
`x-implementation-status: planned`, tag `Admin`, inheriting the global `bearerAuth` security and
gated in code by `@RolesAllowed("admin")` (the same pattern as the existing
`/jobs/admin/triggers*` operations). Naming mirrors `/jobs/admin/triggers`.

- **`GET /jobs/admin/companies`** (`listAdminCompanies`): paginated browse. Query params `q`
  (case-insensitive substring over `name`), `manuallyEdited` (optional provenance filter, the
  enrichment backlog is `manuallyEdited=false`), `sort` (`name-asc` default, `name-desc`,
  `updated-desc`, `updated-asc`), `page` (min 0, default 0), `size` (min 1, max 100, default 20).
  Response `200` is a JSON array of `CompanyInfo` (FULL projection, `description` populated,
  unlike the size-sensitive public `GET /jobs` summary) plus an `X-Total-Count` response header
  with the total match count. Errors: `400`, `401`, `403`, `500`.
- **`GET /jobs/admin/companies/{id}`** (`getAdminCompany`): read one company by `CompanyInfo.id`,
  full projection. `200` `CompanyInfo`; `401`, `403`, `404`, `500`.
- **`PUT /jobs/admin/companies/{id}`** (`updateAdminCompany`): enrich the seven editable fields
  via the new `CompanyUpdateRequest` body. `200` returns the updated `CompanyInfo` with
  `manuallyEdited = true`; `400` (validation), `401`, `403`, `404` (unknown id), `500`.

Pagination-with-`X-Total-Count` (array body + header) was chosen over a body page wrapper because
the task calls for the header-count convention (`CLAUDE.md` REST layer: "use `Response` when a
header must be set, e.g. `X-Total-Count`") and it lets `CompanyInfo` stay the sole response shape
with no new page-wrapper schema.

### D4. Update verb: PUT (full editable-set replace), not PATCH.

`PUT /jobs/admin/companies/{id}` with `CompanyUpdateRequest` carrying all seven editable fields.
Semantics: every editable field is set from the body; a field sent as null (or omitted, which a
generated Jackson model reads as null) CLEARS the stored value to null. `id`, `slug` and `name`
are immutable through this endpoint and ignored if present.

Why PUT over PATCH. A generated Jackson POJO cannot distinguish "key absent" from "key present and
null" (the repo's existing `updateSavedFilter` PATCH already lives with this: a null field means
"leave unchanged", so it can never CLEAR a field). Admin enrichment specifically needs the ability
to clear a wrong value back to null, and its UX is a form that loads every editable field and
submits them together. Full-set replace on PUT is therefore both unambiguous and generator-safe:
null means clear, and there is no absent-vs-null ambiguity. The frozen client rule is: send the
FULL editable set (echo unchanged values back); an omitted field is a clear, not a no-op.

`name` is deliberately NOT editable here. `slug` is the identity key (unique, drives dedup and
logo derivation) and `name` is the display value the crawl captured or a future story curates;
renaming and merging companies is out of scope for #430 (051 already notes "merges are story #430
territory", but this story is scoped by the orchestrator to the seven enrichment fields, so
rename/merge is explicitly deferred). Keeping `name`/`slug` immutable avoids slug divergence.

### D5. `CompanyUpdateRequest` validation (frozen).

- `website`: string, `format: uri`, nullable, `maxLength: 2048`.
- `industry`: string, nullable, `maxLength: 80`. Free text, not an enum (CompanyInfo reason).
- `size`: string, nullable, `maxLength: 40`. Free text; producers should stay in the CompanyInfo
  headcount vocabulary so the UI can group, but it is not enum-constrained.
- `headquarters`: string, nullable, `maxLength: 120`. Never back-filled from a posting location.
- `description`: string, nullable, `maxLength: 2000`.
- `tags`: array, nullable, `maxItems: 20`, items `minLength: 1`, `maxLength: 40`,
  `pattern: ^[a-z0-9]+(-[a-z0-9]+)*$`. Duplicates rejected with `400`, enforced server-side (NOT
  via `uniqueItems`: that made the generator emit a `Set` with a `@JsonDeserialize` whose
  jackson-databind import is absent from the interface-only api-contracts classpath, breaking the
  build; verified). Null or empty array clears all tags (stored as null, never `[]`).
- `logoUrl`: string, `format: uri`, nullable, `maxLength: 2048`. Setting it makes the logo a
  manual value the crawl/derivation must never overwrite (ADR 0024 guard, now record-level).

### D6. Migration: none. `CompanyInfo`: reused unchanged.

No `db/init` migration is needed for #430. Every column the update writes exists from `051`
(`website`, `industry`, `size`, `headquarters`, `description`, `tags`, `logo_url`, `source`,
`manually_edited`, `updated_at`); `source = 'manual'` is already permitted by
`chk_company_source`; `job_user` already holds `SELECT, INSERT, UPDATE ON crawler.company`; and
the browse endpoint is a `SELECT` with `LIMIT/OFFSET` + `COUNT(*)` over a ~150-row table needing
no new index. No new grant, role, schema, password or `.env` key.

Migration-number bookkeeping: the highest committed `db/init` file is `051`. ADR 0024 D5 reserved
`052` (logo backfill, #447) and `053` (job-service follow-up). Should #430 ever need a migration
(it does not under this design), it would claim `054`; nothing here does.

The contract change is purely additive: one new request schema (`CompanyUpdateRequest`) and three
new operations. No existing shared schema is touched, so there is no cross-service blast radius of
the #330 kind; `CompanyInfo` is reused verbatim.

## Consequences

- **Positive.** Zero migration, zero new grant, zero schema/contract-shape change to anything
  existing. The whole story is a job-service REST + persistence extension plus one additive
  request schema. It stays inside the ADR-0023 sole owner of `crawler.company`.
- **Positive.** The override stays structural and cheap: one boolean pins the record; the
  insert-only reconciler physically cannot overwrite; the one standing `WHERE manually_edited =
  false` rule protects every future refresh. No per-field bookkeeping to keep in sync.
- **Positive.** Consistent with ADR 0024: same record-level guard model the logo work used, now
  the general rule for all enrichable fields.
- **Cost / trade-off.** PUT full-set replace means a client that forgets to echo a field will
  clear it. Mitigated by the frozen "load-all, submit-all" form contract and the `400`/typed
  responses; documented on `CompanyUpdateRequest`. This is the deliberate price of being able to
  clear a field on a generated model.
- **Cost / trade-off.** `name`/`slug` are not editable in this story, so fixing a wrong company
  name or merging duplicates is deferred. Acceptable: it is out of #430's scope and needs its own
  identity/merge design.
- **Follow-on rule for developers.** If a later story adds a reconciler or refresh `UPDATE` on
  existing companies, it MUST include `AND manually_edited = false`. This ADR and ADR 0024 both
  record it; it is the single point the record-level model depends on.

## Alternatives considered

- **Per-field provenance (a `text[]` of edited field names or per-field boolean columns).**
  Rejected: no write path updates individual fields of an existing company (the reconciler is
  insert-only, refresh-UPDATE was deferred by ADR 0024), so there is nothing for field-level
  granularity to arbitrate. It would also contradict the frozen `CompanyInfo` (per-field
  provenance is explicitly internal, only `manuallyEdited` is exposed) and add a migration for no
  behavioural gain. Revisit only if and when a field-level crawl refresh path is actually built.
- **PATCH (partial merge) for the update.** Rejected as the primary verb: on a generated Jackson
  model, absent and null collapse, so PATCH could never CLEAR a field (the repo's `updateSavedFilter`
  already has this limitation), yet clearing a wrong value is a core enrichment need. PUT full-set
  replace removes the ambiguity.
- **Body page wrapper (a `CompanyPage` schema) instead of `X-Total-Count`.** Rejected: the task
  and `CLAUDE.md` favour the header-count convention here, and reusing `CompanyInfo` as the array
  item avoids introducing another wrapper schema.
- **Making `name`/`slug` editable now.** Deferred: slug is the identity/dedup/logo-derivation key
  and renaming or merging companies is a separate concern with its own invariants; out of scope
  for #430.
