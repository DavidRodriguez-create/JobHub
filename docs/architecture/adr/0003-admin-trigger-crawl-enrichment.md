# ADR 0003: Admin-only trigger for crawl & enrichment passes

- **Status:** Accepted
- **Date:** 2026-06-06
- **Deciders:** Principal Architect (jobhub-architect); David R H
- **Affects:** job-service (Hexagonal), crawler-service (Hexagonal), auth-service (Clean),
  api-contracts (job-service.yaml + auth-service.yaml), db/init, podman-compose / deploy (DevOps)

## Context

Story #7 (sub-issues #31 auth, #32 crawler, #33 frontend) adds an **admin-only** page that can
manually **trigger a crawl** and **trigger an enrichment** pass and show basic status / last-run.
Triggering must be **enable/disable-able via deployment config (reloadable)** and **optionally
require a 6-digit verification code** before firing. This ADR is the contract-freeze gate the
three tracks build on in parallel.

Locked product decisions (not relitigated here):

1. **Admin identity = a configured email allowlist** (deployment env var). On login, an email in
   the allowlist gets an `admin` group in its JWT; the admin page + endpoints require that group.
   **No `auth.user` schema change** — a DB admin flag was explicitly rejected as hacker-prone.
   Admin-ness is derived purely from config at token-issue time.
2. **Email/verification-code infra from Story #6 (ADR 0002) is in `main`**: `auth.verification_code`
   (hashed 6-digit code, `action` CHECK, expiry, attempts, 429 throttle), the `VerificationAction`
   enum, `EmailVerificationService` / `AccountVerificationService`, and a profile-selected
   `VerificationNotifier`. The optional code gate **reuses** this — no new code mechanism.
3. **Reloadable toggle** to enable/disable triggering + an **optional code gate**, both from config.
4. Out of scope: full RBAC/permissions; per-target scheduling UI. Just "trigger a pass" + status.

The forces that actually constrain the design come from the deploy topology, verified against the
code and the production wiring:

- **crawler-service has no published HTTP port and no JWT.** `podman-compose.yml` and
  `deploy/shared/compose/docker-compose.prod.yml` both label it *"Scheduled batch crawler … No
  JWT, no published port."* The oci-free edge (`deploy/shared/nginx/default.conf`, fronted by
  Caddy) proxies **only** `/auth/`, `/jobs/`, `/applications/` to the three published services;
  crawler sits in the internal-only group with Postgres and Ollama and is **not internet-reachable**.
  Its pom has `quarkus-rest` + `quarkus-rest-client-jackson` but **no `quarkus-smallrye-jwt`**.
- **job-service is already JWT-verifying and internet-exposed.** Its pom has `quarkus-smallrye-jwt`
  + `quarkus-test-security-jwt` + the JWT public-key plugin; `JobResource` already gates endpoints
  with `@RolesAllowed("user")`. It is proxied at `/jobs/` and publishes `8081`.
- **job-service already writes the `crawler` schema cross-schema.** `SavedJobEntity` /
  `SavedFilterEntity` are `@Table(schema = "crawler")`, and `010-crawler.sql` grants
  `job_user` `SELECT, INSERT, UPDATE, DELETE` on those tables. A control table in `crawler` written
  by job-service is therefore an **existing, proven pattern**, not a new one.
- **The crawl/enrich use cases are synchronous and directly callable.**
  `CrawlUseCase.crawlBatch(int)` → `CrawlBatchResult` and `EnrichJobsUseCase.enrichPending(int)` →
  `int`. A trigger needs only to *invoke* them; it adds no new domain logic to the crawler.
- **A reusable service-to-service code-consume endpoint already exists.**
  `POST /auth/account/verifications/consume` (`consumeVerification`) is documented as the internal
  endpoint other services call to validate+consume a code for an action they own
  (application-service already uses it via its `AuthServiceRestClient`). It returns **204** on
  valid+consumed, **400** on invalid/expired/used/action-mismatch, keyed to the Bearer-token user.

job-service and crawler-service are **Hexagonal**; auth-service is **Clean**. The architecture per
service is unchanged by this story — this is an additive feature within each.

## Decision

We will host the admin trigger + status API on **job-service** (already JWT-secured and
internet-exposed) and have it **signal crawler-service through a control-table row in the
`crawler` schema**, which a new crawler poller claims and executes. This is **alternative (C)
for the API host composed with alternative (B) for the crawler signal**. crawler-service stays
**non-public, JWT-free, and port-free** — exactly as the deploy docs promise.

### 1. Trigger mechanism — control table polled by the crawler

- New table **`crawler.trigger_request`** (one row per admin-requested pass): `id`,
  `kind` (`crawl`|`enrichment`), `status` (`queued`|`running`|`succeeded`|`failed`),
  `requested_by` (admin user UUID from the JWT subject), `requested_at`, `started_at`,
  `finished_at`, `result_summary`, `error_reason`, plus a `locked_by`/`lease_expires_at` lease
  pair mirroring `crawler.pull_target` so multiple crawler instances don't double-run a request.
  It lives in the **crawler** schema because crawler owns the control-plane lifecycle and reads
  it with no cross-schema grant; `016-crawler-trigger-request.sql` grants `job_user`
  `SELECT, INSERT` (and `SELECT` for status reads) — the same least-privilege shape as saved jobs.
- **job-service** (Hexagonal): `POST /jobs/admin/triggers` runs the gates (below), then through a
  new out-port (`TriggerRequestRepository` → a Panache adapter writing `crawler.trigger_request`)
  inserts a `queued` row and returns **202** with the row. `GET /jobs/admin/triggers/status`
  reads back per-kind last-run + the two config flags via the same port.
- **crawler-service** (Hexagonal): a new `adapter/in/scheduler/TriggerRequestScheduler` (poll
  ~10 s) claims the oldest `queued` row per kind via a new out-port
  (`TriggerRequestQueue` with `claimNext` / `markRunning` / `markDone`), sets `running`, calls the
  **existing** `crawlBatch` / `enrichPending` use case, then writes `succeeded`/`failed` +
  `result_summary`. No inbound HTTP, no JWT, no new domain logic on the crawler.

### 2. Admin identity — config allowlist → JWT group (no DB change)

- New config `auth.admin.emails` (CSV, env `AUTH_ADMIN_EMAILS`, default empty). At token issue,
  `SmallryeJwtTokenGenerator.generate(user)` adds `"admin"` to the existing
  `.groups(Set.of("user"))` **iff** the user's (normalised, case-insensitive) email is in the
  allowlist. Nothing is persisted; `auth.user` is untouched.
- `AccountResponse` gains **`isAdmin`** (the convenience flag the UI gates on) and **`groups`**
  (the full group list), both derived from the same rule via `AccountResponseMapper`. The admin
  page shows itself off `account.isAdmin`; the endpoints enforce the `admin` group server-side
  regardless of the flag.

### 3. "Reloadable" — pinned, testable semantics

Quarkus `@ConfigProperty` is resolved at startup; the gates are **re-read on every request**, but
the *value* is fixed for the life of a running container. So **"reload the deployment variables"
means: change the env var in the deploy environment and recreate the container
(`compose up -d <svc>`), which re-reads env with no image rebuild and no code change** — the same
operational pattern the repo already uses for `CRAWLER_ENRICHMENT_ENABLED`. This is what
acceptance tests assert: with `JOBHUB_ADMIN_TRIGGER_ENABLED=false` the running job-service returns
**403** from `POST /jobs/admin/triggers` and `triggerEnabled:false` from the status endpoint;
recreating it with `=true` flips both. We deliberately **do not** implement true hot-reload (a
polling `ConfigSource`): it adds a moving part for a control that flips rarely, and a few-second
container recreate is acceptable for an admin toggle.

Config keys (job-service `application.properties`, with env mappings for prod):

| Property | Env | Default | Meaning |
|---|---|---|---|
| `jobhub.admin.trigger.enabled` | `JOBHUB_ADMIN_TRIGGER_ENABLED` | `true` | Master enable for triggering. |
| `jobhub.admin.trigger.require-code` | `JOBHUB_ADMIN_TRIGGER_REQUIRE_CODE` | `false` | Turns on the optional code gate. |
| `auth.admin.emails` (auth-service) | `AUTH_ADMIN_EMAILS` | *(empty)* | CSV allowlist → `admin` group. |
| `crawler.trigger.poll-cron` (crawler) | `CRAWLER_TRIGGER_POLL_CRON` | `0/10 * * * * ?` | Control-table poll cadence. |

### 4. Optional code gate — reuse #6, two-phase, service-to-service

When `jobhub.admin.trigger.require-code=true`:

1. UI → auth `POST /auth/account/verifications { action: "admin-trigger" }` → `{ verificationId,
   expiresAt }`; a 6-digit code is emailed (15-min TTL).
2. UI → job-service `POST /jobs/admin/triggers { kind, verificationId, code }`.
3. job-service → auth `POST /auth/account/verifications/consume { verificationId, code,
   action: "admin-trigger" }`, **forwarding the admin's Bearer token**. **204** ⇒ record the
   request and return 202; **400** ⇒ return **422** (`Verification Required`).

This adds **no new auth endpoint** and **no new code mechanism** — only the value `admin-trigger`
in the `VerificationAction` enum / `verification_code.action` CHECK and the two OpenAPI action
enums. job-service gains an outbound `quarkus-rest-client` + an `AuthServiceRestClient`
(the pattern application-service already proves). When `require-code=false`, the body omits
`verificationId`/`code` and job-service skips step 3.

### 5. Contract (frozen; all new/changed ops & properties `x-implementation-status: planned`)

**auth-service.yaml** (additive, backward-compatible):
- `AccountResponse` gains `isAdmin: boolean (default false)` and `groups: string[]`.
- `VerificationRequest.action` and `ConsumeVerificationRequest.action` enums gain `admin-trigger`.

**job-service.yaml** (new `Admin` tag; operations land in the single generated `JobsApi`):
- `POST /jobs/admin/triggers` — `@RolesAllowed("admin")`; body `TriggerRequestBody
  { kind: crawl|enrichment, verificationId?, code? }`; **202** `TriggerResponse`.
- `GET /jobs/admin/triggers/status` — `@RolesAllowed("admin")`; **200**
  `TriggerStatusResponse { triggerEnabled, codeRequired, crawl?, enrichment? : TriggerRunInfo }`.

### 6. Error model (pinned)

| Condition | Status | `error` title | Notes |
|---|---|---|---|
| No / invalid Bearer token | **401** | (policy) | Standard JWT denial. |
| Authenticated, not in `admin` group | **403** | (policy) | Standard `@RolesAllowed` denial. |
| Triggering disabled (`enabled=false`) | **403** | `Triggering Disabled` | Distinct *body* from the policy 403 so the UI can message it; status endpoint also reports `triggerEnabled:false`. |
| Malformed body (unknown `kind`, bad code shape) | **400** | `Validation Error` | Bean-validation / enum parse. |
| Code gate on, code missing/invalid/expired/used/wrong-action | **422** | `Verification Required` | Maps auth-consume **400** → 422 at the job-service boundary. |
| Auth-side verification throttle | **429** | `Too Many Requests` | Propagated from the code flow. |
| Same-kind pass already queued/running | **409** | `Trigger In Progress` | Optional dedupe; surface the in-flight run. |
| Unexpected | **500** | `Internal Server Error` | `GenericExceptionMapper`. |

### 7. Migrations

- **crawler-dev (#32):** `db/init/016-crawler-trigger-request.sql` — `CREATE TABLE
  crawler.trigger_request (…)` + a `chk_trigger_request_kind`/`…_status` CHECK + an index for the
  poll (`(kind, status, requested_at)` partial on `status='queued'`) + `GRANT SELECT, INSERT ON
  crawler.trigger_request TO job_user`. Crawler range is **010–019**; **016** is free.
- **auth-dev (#31):** `db/init/023-auth-admin-trigger-action.sql` — widen the
  `verification_code.action` CHECK to include `admin-trigger` (the same shape as `022`). Auth
  latest is **022**; **023** is next. Otherwise auth-dev is config + JWT-group logic only.
- **applications** range (030) is untouched.

### 8. Security

crawler-service stays internal: **no inbound port, no JWT, not in the nginx/Caddy proxy map** —
the attack surface of the background crawler is unchanged. The only exposed write path is
job-service's already-authenticated `/jobs/` surface, now `admin`-gated. The allowlist and any
SMTP/mailer secrets are env-only and **never committed** (consistent with ADR 0002 and the
oci-free credential model). The control row records `requested_by` for a basic audit trail.

## Consequences

- Positive: crawler-service keeps its "portless background worker" invariant intact — no JWT
  extension, no published port, no proxy entry, no new domain logic (it reuses `crawlBatch` /
  `enrichPending`). The deploy topology and threat model are unchanged.
- Positive: the API host (job-service) already has the JWT stack, the proxy entry, and a
  cross-schema-write precedent, so the surface is additive and idiomatic — one new table, two new
  endpoints on an existing resource, two reused config flags.
- Positive: the code gate reuses #6 end-to-end (issue → consume) including the existing
  service-to-service consume endpoint and the 429 throttle; no second challenge mechanism.
- Negative / cost: "trigger" is **asynchronous** (poll latency ≈ the poll cron, ~10 s) rather than
  a synchronous "run now". The status endpoint + `TriggerRunInfo` exist precisely to make this UX
  honest; this matches the explicit "reliable, not fast" directive.
- Negative / cost: job-service gains an **outbound REST client to auth-service**
  (`quarkus-rest-client` + `AuthServiceRestClient` + token propagation) — new for job-service,
  though proven in application-service — and therefore a WireMock dependency for its client tests
  (job-service previously had none). Only needed when the code gate is exercised.
- Negative / cost: a new owned table (`crawler.trigger_request`) and a new crawler poller to
  maintain; a lease column set to keep multi-instance safe.
- Negative / cost: `isAdmin`/`groups` correctness now depends on the allowlist being set in the
  deploy env; a misconfigured/empty allowlist silently yields no admins (intended fail-closed).
- Follow-ups: auth-dev #31 (allowlist config + JWT-group rule + `AccountResponseMapper` + enum +
  migration 023), crawler-dev #32 (table migration 016 + `TriggerRequestQueue` port + poller +
  done/failed bookkeeping), job-dev/frontend #33 (the two endpoints with `@RolesAllowed("admin")`,
  the `TriggerRequestRepository` port + cross-schema adapter, the optional `AuthServiceRestClient`,
  and the admin page driven by `account.isAdmin` + the status endpoint). DevOps wires the new env
  keys and mounts `016` (crawler) and `023` (auth) in compose in numeric order.

## Alternatives considered

- **(A) Inbound admin REST endpoint on crawler-service.** Rejected. It forces crawler to add
  `quarkus-smallrye-jwt` + the JWT public-key plugin, **publish a port**, and be added to the
  nginx/Caddy proxy map and the Vite proxy — directly violating the deploy invariant *"No JWT, no
  published port"* and widening the attack surface of a service that is currently unreachable from
  the internet. That is the network-layer version of the "hacker-prone" exposure the product owner
  rejected for a DB admin flag. Its only upside (synchronous "run now") is not worth the exposure.
- **(B) hosted *on crawler* (admin writes the control row directly).** Rejected as a host: crawler
  has no exposed write path at all, so the admin's insert still has to happen on an exposed,
  JWT-verifying service — which is job-service. (B) is the right *crawler signal*; it does not by
  itself answer *where the admin API lives*. Our decision keeps (B)'s table and adopts (C)'s host.
- **(C) on auth-service instead of job-service.** Viable — auth is exposed and JWT-aware — but
  auth is the **Clean** domain service for identity; a crawl/enrichment trigger is not an identity
  concern and would couple auth to the `crawler` schema. job-service already reads/writes `crawler`
  and already serves job-domain reads, so it is the better-fitting host.
- **Synchronous trigger via a direct job-service → crawler internal HTTP call.** Rejected: still
  requires crawler to expose a port + verify a token (or trust an unauthenticated internal call),
  reintroducing (A)'s exposure for the sake of removing a ~10 s poll delay.
- **True hot-reload of the toggle (polling `ConfigSource`, no restart).** Rejected for now: extra
  machinery for a rarely-flipped admin control; env-change + container-recreate is simpler and
  testable. Recorded as the fallback if operators later need zero-restart toggling.
- **New admin-flag column on `auth.user`.** Rejected by product (hacker-prone) and unnecessary —
  the allowlist-at-token-issue rule needs no schema and no migration on `auth.user`.
- **A brand-new api-contracts spec / module for the admin surface.** Rejected: the admin API lives
  on job-service, so it belongs in `job-service.yaml`; no new generator execution or module pom is
  warranted. (auth changes likewise stay in `auth-service.yaml`.)
