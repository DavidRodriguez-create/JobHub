# ADR 0019: Admin crawl/enrichment trigger gated by the admin's own 2FA

- **Status:** Accepted
- **Date:** 2026-07-20
- **Deciders:** Principal Architect (jobhub-architect); David R H
- **Affects:** job-service (Hexagonal), auth-service (Clean), api-contracts
  (job-service.yaml + auth-service.yaml), db/init, JobHub-ui
- **Supersedes:** ADR 0003 section 4 (the optional emailed request-code gate) and its
  related pieces of sections 5 to 7. ADR 0003's core decision (trigger hosted on job-service,
  signalling crawler through the `crawler.trigger_request` control table, the enabled gate,
  and the admin allowlist to `admin` JWT group) is unchanged.

## Context

Story #384 (sub-issues: #385 this contract freeze, #388 auth backend, #389 job backend, plus
PDA/QAE/UI tickets). The admin crawl/enrichment trigger shipped in ADR 0003 with an **optional
emailed 6-digit "request code" gate** (`jobhub.admin.trigger.require-code`): to fire a trigger
the admin first requested a code via `POST /auth/account/verifications { action: admin-trigger }`,
received it by email, then passed `{ verificationId, code }` to `POST /jobs/admin/triggers`,
which job-service consumed service-to-service via `POST /auth/account/verifications/consume`.

The product owner judged the emailed request-code path **overkill**: it is a second,
purpose-built challenge mechanism bolted onto an admin action, with an email round-trip, when
the platform **already has strong per-account 2FA** (TOTP + backup codes, ADR 0012). An admin
who has enabled 2FA already holds an authenticator; an admin who has not has made that choice
for their account. The security of a sensitive admin action should ride on the admin's own 2FA
posture, not on a bespoke emailed code.

Constraints and existing facts this builds on (verified against the tree):

- **auth-service already owns full TOTP 2FA** (ADR 0012, `db/init/024-auth-2fa-totp.sql`):
  `auth.user.two_factor_enabled`, `auth.totp_secret`, `auth.totp_backup_code`,
  `auth.two_factor_challenge`. The verification logic exists and is reused by the login-2fa
  path: `application/usecase/VerifyLoginTwoFactorService`, `application/usecase/TwoFactorCodeMatcher`,
  and the `application/port/out/TotpCodeVerifier` (`adapter/out/security/TotpCodeVerifierAdapter`).
  **We reuse this; we do not re-spec any crypto.**
- **auth already exposes `X-Service-Key`-guarded internal endpoints** (`serviceKeyAuth` scheme,
  `JOBHUB_INTERNAL_SERVICE_KEY`): e.g. `GET /internal/users/emails`, `GET /internal/users/without-2fa`.
  A new internal endpoint is an established, least-surface pattern.
- **job-service already has the admin's identity as the JWT subject.** ADR 0003 records
  `requested_by` as "admin user UUID from the JWT subject". The auth `user.id` *is* the JWT
  `sub`. So job-service can name the admin to auth service-to-service **by userId**, with no
  extra lookup.
- **job-service already calls auth service-to-service** (`domain/port/out/VerificationGateway`
  → `adapter/out/client/VerificationGatewayAdapter` → `AuthServiceRestClient`) and already had
  the WireMock dependency for its client tests. This change repoints that outbound client at
  the new internal endpoints; it does not add a new outbound-HTTP capability.
- **auth-service runs at root-path `/auth`.** Any rest-client from job-service must bake `/auth`
  into its `@Path` or it 404s (a known past failure mode). Recorded here so developers do not
  relearn it.

Locked product rules (confirmed with the human):

1. **Gate:** if the triggering admin has 2FA enabled, they must supply a valid TOTP code (or a
   backup code) to fire a trigger. If they have no 2FA enabled, the trigger fires directly with
   no extra step. Security is delegated to the admin's own 2FA choice.
2. **Remove the emailed request-code path end-to-end:** the auth `admin-trigger` verification
   action, job-service's `consume` call for it, and the UI "Request Code" step all go away.

## Decision

Replace the optional emailed request-code gate with a **2FA-conditional gate keyed on the
triggering admin's own account 2FA**, verified by job-service against auth-service
service-to-service. The enabled gate, the control-table trigger mechanism, and the admin
allowlist from ADR 0003 are untouched.

### 1. Two new internal (service-to-service) auth endpoints

Both are `serviceKeyAuth` (`X-Service-Key`), under the existing `/internal/...` convention,
and, because auth is root-pathed at `/auth`, resolve at `/auth/internal/...`. The admin is
named explicitly by **`userId`** (the JWT `sub` job-service extracted from the admin's token):
service-to-service calls carry no user JWT, and userId is already in job-service's hand and is
auth's natural key (mirrors `/internal/users/emails`, `/internal/users/without-2fa`). We chose
**userId over email**: it is directly available, needs no lookup, and avoids leaking/handling
the address on this path.

**Two endpoints, not one**, because job-service has two distinct needs:

- `GET /internal/users/{userId}/two-factor` → `200 TwoFactorStatusResponse { twoFactorEnabled }`.
  A **no-code status read** used by `GET /jobs/admin/triggers/status` to tell the UI whether to
  show a code input. Errors: `400` bad uuid, `401` bad service key, `404` unknown user, `500`.
- `POST /internal/two-factor/verify` → body `VerifyTwoFactorRequest { userId, code? }`, returns
  `200 VerifyTwoFactorResponse { outcome: verified | not_enrolled }`. The **authorization
  decision** used by `POST /jobs/admin/triggers`. `verified` = 2FA enabled and code valid;
  `not_enrolled` = no 2FA (proceed directly). Errors: `400` malformed body, `401` bad service
  key, `404` unknown user, **`422`** 2FA enabled and code missing/invalid/expired/used, `429`
  attempt throttle, `500`. It **reuses** the existing TOTP + backup-code verification (valid
  TOTP verifies without side effect; a valid backup code is consumed single-use; failures are
  throttled).

A single collapsed endpoint was rejected: the status panel legitimately needs a **side-effect-
free, throttle-free** read, and overloading the verify POST for that would abuse a
state-changing/throttled operation for a query. `422` (not `401`) carries "bad code" so it never
collides with the `401` that `serviceKeyAuth` failure already returns on the same operation.

### 2. Layering (no blending)

**auth-service (Clean).** Add two use cases in `application/usecase/` behind new
`application/port/in/` interfaces:
- a 2FA-status query (e.g. `GetTwoFactorStatusUseCase` + service), reading `TotpSecretRepository`
  / `auth.user.two_factor_enabled`;
- a service-to-service verify (e.g. `VerifyTwoFactorForServiceUseCase` + service) that takes a
  command `{ userId, code }` and **reuses `TwoFactorCodeMatcher` / `TotpCodeVerifier` /
  `TotpSecretRepository`** (the same collaborators `VerifyLoginTwoFactorService` uses). No new
  domain entity and no new crypto. The two endpoints live on the existing
  `adapter/in/rest/InternalUserResource` (Layer 3, `serviceKeyAuth`), with request/response
  DTOs in `adapter/in/rest/dto/` mapped to the commands. Bad-code and throttle map to HTTP via
  `@Provider ExceptionMapper`s (reuse/extend the existing `InvalidTotpCodeException` /
  `TwoFactorNotEnabledException` mappers; do not build responses in the resource).

**job-service (Hexagonal).** Keep the outbound port in `domain/port/out/` and its adapter in
`adapter/out/client/`. Replace the `VerificationGateway` semantics with a 2FA-oriented out-port
(e.g. `AdminTwoFactorGateway` with `boolean isEnabled(UUID userId)` and an `verify(UUID userId,
String code)` returning an authorized/denied result), implemented by an adapter over
`AuthServiceRestClient` now pointing at `/auth/internal/users/{userId}/two-factor` and
`/auth/internal/two-factor/verify` (both with the `X-Service-Key` header from config, and
`/auth` baked into the `@Path`). `domain/service/AdminTriggerService` drives the gate: the POST
path resolves the admin's userId from the JWT subject and calls `verify`; the status path calls
`isEnabled` to set `twoFactorRequired`. DTOs stay at the REST boundary; the domain stays
annotation-free. No handler injects an adapter directly; the service depends only on the port.

### 3. Contract changes (frozen; new/changed items `x-implementation-status: planned`)

**auth-service.yaml**
- New schemas `TwoFactorStatusResponse`, `VerifyTwoFactorRequest`, `VerifyTwoFactorResponse`.
- New ops `getUserTwoFactorStatus` (`GET /internal/users/{userId}/two-factor`) and
  `verifyUserTwoFactor` (`POST /internal/two-factor/verify`), tag `Internal`, `serviceKeyAuth`.
- Removed `admin-trigger` from the `VerificationRequest.action` and
  `ConsumeVerificationRequest.action` enums and from the `consumeVerification` description.
  `delete-account` / `delete-all-applications` are unchanged (application-service still uses
  the consume endpoint for those).

**job-service.yaml**
- `TriggerRequestBody`: dropped `verificationId`; `code` is now the admin's **optional** own
  2FA code, pattern `^\d{6}$|^[a-zA-Z0-9]{8}$` (TOTP or backup code).
- `TriggerStatusResponse`: dropped `codeRequired` (a deployment flag); added
  **`twoFactorRequired`** (a **per-caller** boolean: does THIS admin have 2FA). This is a
  deliberate breaking rename of a `planned` field; the UI ticket adapts.
- `POST /jobs/admin/triggers`: description now documents the 2FA gate; `422` means "admin has
  2FA and the supplied code was missing/invalid/expired/used"; `429` is the auth-side 2FA
  attempt throttle. No `verificationId` anywhere.

### 4. Migrations

| Ticket | Number | Change |
|---|---|---|
| auth backend #388 | **`048-auth-drop-admin-trigger-action.sql`** | Forward-only. Narrow the `auth.verification_code.action` CHECK back to `('verify-email', 'delete-account', 'delete-all-applications')`, removing `'admin-trigger'` (reverses `023`). Must first `DELETE FROM auth.verification_code WHERE action = 'admin-trigger'` so the re-added CHECK does not fail on residual rows (safe: codes are short-lived, 15-min TTL). Same drop-then-add shape as `023`. Prod runs Hibernate `validate`, so this file is the schema source; dev/test drop-and-create from entities and skip it. |
| job backend #389 | **N/A** | No schema change. The gate is logic-only: repoint the outbound client and change `AdminTriggerService`. No new table/column in `job` or `crawler`. |

`048` is the next free number (latest is `047-pg-stat-statements.sql`). No column is added for
2FA state on the trigger path: the source of truth stays in the `auth` schema (`totp_secret` /
`two_factor_enabled`), read live per request. DevOps mounts `048` in compose in numeric order;
on an existing volume it is applied by hand (`podman exec -i jobhub-db psql -U jobhub -d jobhub
< db/init/048-auth-drop-admin-trigger-action.sql`) then auth-service restarted.

### 5. Error model (job-service POST /jobs/admin/triggers, updated)

| Condition | Status | `error` title |
|---|---|---|
| No / invalid Bearer token | 401 | (policy) |
| Authenticated, not in `admin` group | 403 | (policy) |
| Triggering disabled (`enabled=false`) | 403 | `Triggering Disabled` |
| Malformed body (unknown `kind`, `code` fails pattern) | 400 | `Validation Error` |
| Admin has 2FA and code missing/invalid/expired/used | 422 | `Verification Required` |
| Auth-side 2FA attempt throttle | 429 | `Too Many Requests` |
| Same-kind pass already queued/running | 409 | `Trigger In Progress` |
| Unexpected | 500 | `Internal Server Error` |

## Consequences

- Positive: one fewer bespoke security mechanism. No emailed request code, no
  `verificationId` handshake, no `POST /auth/account/verifications { action: admin-trigger }`.
  The gate reuses proven per-account 2FA and honours the admin's own posture.
- Positive: auth changes are additive (two internal endpoints reusing existing verification)
  plus one narrowing migration. job changes are logic-only, no schema. Both services keep their
  architecture: job Hexagonal, auth Clean.
- Positive: `twoFactorRequired` is resolved per admin, so the UI shows the code input only to
  admins who actually have 2FA; non-2FA admins get a one-click trigger.
- Neutral / by design: an admin with **no** 2FA fires the trigger with no second factor. That
  is the explicit product rule (security delegated to the admin's own choice); if a hard second
  factor for all admins is later required, that is a follow-up (e.g. require 2FA enrollment for
  allowlisted admins), not this ADR.
- Cost: a breaking rename of the `planned` `codeRequired` field to `twoFactorRequired` and a
  changed `code` shape; the UI and any status consumers must adapt. Acceptable while the trigger
  feature is still `planned`.
- Cost: the status panel adds one extra service-to-service round-trip (the 2FA-status read) per
  status fetch. Cheap, cacheable if ever needed, and off the hot job-search path.

## Alternatives considered

- **Keep the emailed request-code gate.** Rejected by the product owner as overkill given
  first-class 2FA already exists; it is a second challenge mechanism with an email round-trip.
- **Single collapsed verify endpoint (no status read).** Rejected: the status panel needs a
  side-effect-free, throttle-free read; abusing the verify POST for a query conflates a
  state-changing/throttled operation with a status lookup.
- **Name the admin by email instead of userId.** Rejected: userId (JWT `sub`) is already in
  job-service's hand and is auth's natural key; email needs a lookup and needlessly handles the
  address on the trigger path.
- **`401` for a bad code on the verify endpoint.** Rejected: it would collide with the `401`
  that `serviceKeyAuth` failure returns on the same operation; `422` keeps "bad service key" and
  "bad 2FA code" cleanly distinguishable for the rest-client.
- **Store admin 2FA state in the `crawler`/`job` schema.** Rejected: 2FA state is owned by the
  `auth` schema; reading it live service-to-service keeps a single source of truth and adds no
  cross-schema coupling.
