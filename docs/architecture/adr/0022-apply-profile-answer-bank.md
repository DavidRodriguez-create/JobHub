# ADR 0022: Apply-profile answer bank (auth-service)

- **Status:** Accepted
- **Date:** 2026-07-22
- **Deciders:** jobhub-architect (design), David R H (owner)
- **Affects:** auth-service, api-contracts, db/init, JobHub-ui

## Context

Story #336 asks JobHub to store the roughly ten answers every external ATS repeatedly
demands (work authorization, notice period, salary expectation, current location,
professional links, languages, and career-growth aspirations) once per user, then let
the user view/edit/save them and copy any single field to the clipboard while filling an
external job form.

Constraints and conventions in scope:

- **auth-service is Clean architecture.** The answer bank is user-owned identity-adjacent
  data, so it belongs in the `auth` schema and behind auth-service's concentric layers
  (domain entity, application use cases + ports, adapters). No framework annotations below
  Layer 3.
- **Contract-first.** The API surface lives in `api-contracts/.../openapi/auth-service.yaml`
  with `x-implementation-status: planned`, and the frozen contract is what the backend and
  frontend developers build against in parallel.
- **DB owned by SQL.** Schema is a new numbered file under `db/init/`; Hibernate is
  `validate` in prod.
- **Do not collide with story #296.** #296 wires the existing `PATCH /auth/account`
  (name/email) so those Settings fields actually save. The apply-profile is a distinct
  surface: it must not touch `UpdateAccountRequest`, `AccountResponse`, or the `PATCH`
  handler, or the two stories will fight over the same files.

Two design questions had to be resolved: (1) fixed typed columns vs a generic key/value
answer store, and (2) the story's "per role profile" wording, i.e. one profile per user vs
one per targeted role.

## Decision

We will add a **single per-user apply profile** to auth-service, modelled with **fixed
typed columns** (not key/value), exposed through **two new authenticated endpoints** and a
**new `auth.apply_profile` table**.

**Data shape: fixed typed columns.** The answer set is a stable, curated list, so each
answer is a named, individually-validated field rather than an opaque key/value bag.
Frozen contract fields (all optional, the bank is filled incrementally):
`workAuthorization`, `requiresSponsorship`, `noticePeriod`, `salaryExpectation`,
`currentLocation`, `willingToRelocate`, `linkedinUrl`, `githubUrl`, `portfolioUrl`,
`languages` (list of strings), `roomToGrow`. This keeps per-field Bean-Validation, a clean
domain entity, and a copy-to-clipboard-per-field UI trivial.

**Scope: one profile per user (1:0..1 with `auth.user`).** The listed answers are
user-level facts that do not vary by role. Per-role profiles multiply UI and data
complexity for marginal benefit today (YAGNI). The table carries `UNIQUE (user_id)` to
enforce the single profile; a future per-role variant can drop that unique and add a
nullable role discriminator or a child table without breaking the contract.

**Contract (frozen, `x-implementation-status: planned`):**
- `GET /auth/account/apply-profile` -> `operationId getApplyProfile` -> `200
  ApplyProfileResponse` (always 200, all-null fields when never saved; never 404), `401`.
- `PUT /auth/account/apply-profile` -> `operationId saveApplyProfile` -> body
  `ApplyProfileRequest`, `200 ApplyProfileResponse`, `400`, `401`. **PUT** (full-replace
  upsert) is deliberately chosen over PATCH to signal "save the whole bank" and to stay
  clearly distinct from the #296 `PATCH /account`.

**Persistence:** new migration `db/init/050-auth-apply-profile.sql` creates
`auth.apply_profile` (see the backend build-out spec on ticket #418). `languages` is stored
as `JSONB` and mapped to `List<String>`. Grants are already covered by the
`ALTER DEFAULT PRIVILEGES ... TO auth_user` in `001-schemas.sql`, matching migrations
022/024; no explicit GRANT is added.

**Clean layering (backend build-out):** `domain/entity/ApplyProfile` (guard/replace
method, no JPA); `application/port/out/ApplyProfileRepository`;
`application/usecase/GetApplyProfile` + `application/usecase/SaveApplyProfile` handlers;
`adapter/in/rest/ApplyProfileResource` + DTO mapper; `adapter/out/persistence`
entity/mapper/repository.

> Correction (post-build-out): the two ops live on the existing `AccountResource`, not a standalone `ApplyProfileResource`; the generator merges them into the single `AccountApi` interface via the shared `Account` tag, so a second implementer would double-register every `/account` route. This supersedes the `ApplyProfileResource` mention above.

**UI surface:** there is no Apply Hub screen. The bank is edited-once, copied-often
reference data, so it becomes a new **"Apply profile" section inside the existing Settings
screen** (`JobHub-ui/src/screens/SavedSettings.jsx`, section key `apply-profile`),
alongside `account` and `notifications`, each field with a copy-to-clipboard button.

## Consequences

- Positive: typed contract gives both developers a precise, validated target; the feature
  is fully isolated from #296 (different endpoints, table, DTOs), so the two stories merge
  cleanly.
- Positive: Clean layering keeps the answer bank testable in isolation (handler unit tests
  with a mocked repository) and the schema owned by `db/init`.
- Negative / cost: fixed columns mean adding a future answer type is a contract change plus
  a migration, not a free-form insert. Accepted: the answer set is stable and small.
- Negative / cost: a new table and a new Settings section to maintain.
- Follow-ups: backend implements the migration + Clean slices (ticket #418); frontend adds
  the Settings section + `api/auth.js` `getApplyProfile`/`saveApplyProfile`; QAE covers
  GET-default-empty, PUT-upsert, PUT-validation-400, and 401 paths.

## Alternatives considered

- **Key/value answer store** (`apply_answer(user_id, key, value)`) — rejected: loses
  per-field validation and typing, pushes shape into runtime, and buys flexibility the
  curated answer set does not need.
- **Per-role profiles now** — rejected: the listed answers are user-level; multiple
  profiles add real UI/data cost for marginal gain. The unique constraint leaves the door
  open to add it later.
- **Fold answers onto `AccountResponse` / `PATCH /account`** — rejected: collides directly
  with story #296 and overloads the account identity surface with unrelated ATS answers.
- **A new dedicated Apply Hub screen** — rejected for now: edit-once reference data fits the
  existing Settings surface; a standalone screen is disproportionate.
- **PATCH instead of PUT for save** — rejected: PATCH is already the #296 account pattern;
  PUT (full replace) matches "save the bank" semantics and keeps the two surfaces visibly
  distinct.
