# ADR 0009: Ghosted Alert — Service-Key-Authenticated Internal Status Update

- **Status:** Proposed
- **Date:** 2026-06-18
- **Deciders:** Principal Architect (jobhub-architect); David R H
- **Affects:** application-service, notification-service, api-contracts

## Context

Story #82 (US 5: Ghosted Alert) adds a daily scheduler to notification-service that
auto-marks applications `GHOSTED` after 14 days of silence. The scheduler:

1. Asks application-service for non-terminal applications whose `updatedAt` is older than
   `days` (default 14).
2. For each, sets the status to `ghosted`, then creates an in-app notification and (if the
   user opted in) an email.

Step 2 requires notification-service to **change an application's status**. The only existing
status-change endpoint is `PATCH /applications/{id}/status`, which is authenticated by a user
**Bearer JWT** and scoped to the owning user (it 404s when the application belongs to another
user). notification-service runs a scheduler on behalf of the **system**, not a user: it holds
no user JWT and cannot impersonate the application's owner.

JobHub already has an established pattern for this exact situation. ADR 0008 introduced the
`/internal/*` namespace authenticated by a pre-shared `X-Service-Key` header
(`serviceKeyAuth`), guarded service-wide by `ServiceKeyFilter`, for service-to-service calls
that have no user context. application-service already exposes
`GET /internal/users/{userId}/interest-profile` under this pattern.

Conventions in scope: contract-first api-contracts (interface-only generation); Hexagonal
architecture for the technical services (job-service, crawler-service, application-service,
notification-service); schema-per-service; DB owned by `db/init/` SQL.

## Decision

We will add a **service-key-authenticated** status-update endpoint to application-service
rather than reuse or relax the user-JWT endpoint:

- **`PUT /internal/applications/{id}/status`** — `serviceKeyAuth`, body
  `{ "status": "ghosted" }` (reuses the existing `UpdateApplicationStatusRequest` /
  `ApplicationStatus` enum). It identifies the application by id alone with **no owner check**,
  because the caller is a trusted backend service. It returns a minimal
  `InternalStatusUpdateResponse` (`id`, `userId`, `newStatus`) so notification-service can
  notify the right user without a second round-trip.

We pair it with **`GET /internal/applications/stale`** (`serviceKeyAuth`, `days` query param
default 14) returning non-terminal applications past the inactivity window with display-ready
fields (`id`, `userId`, `jobTitle`, `company`, `currentStatus`, `daysSinceLastActivity`).

Both operations are tagged `Internal`, marked `x-implementation-status: planned`, and live
only in api-contracts for now. The owning logic and persistence stay in application-service
(its schema, its data); notification-service consumes them via an outbound rest-client.

Rationale: this keeps user-facing and system-facing status changes as **two distinct
endpoints with two distinct auth models**. We do not weaken `PATCH /applications/{id}/status`
by making it accept a service key (which would blur user-owner semantics) and we do not let
notification-service reach across schema boundaries to mutate `applications` data directly.

## Consequences

- Positive: Reuses the established ADR-0008 `/internal/*` + `X-Service-Key` pattern; no new
  auth infrastructure, no token minting in notification-service.
- Positive: Schema-per-service isolation is preserved — the status transition and `endedAt`
  stamping stay inside application-service's domain/persistence.
- Positive: User-JWT and service-key status changes remain separate, so the owner-scoping of
  the user endpoint is never relaxed.
- Negative / cost: A second status-update path in application-service to maintain and test
  (component test for `X-Service-Key` validation + the no-owner-check path).
- Negative / cost: The internal endpoint can set **any** status, not just `ghosted`. The
  contract does not restrict it; application-service should treat it as a general trusted
  status update and rely on the caller. A tighter `ghosted`-only variant was considered
  unnecessary (see alternatives).
- Follow-ups:
  - Developer (application-service): implement both operations — resource methods, the stale
    query (non-terminal + `updatedAt < now - days`, computing `daysSinceLastActivity`), and
    the no-owner-check status update reusing the existing transition logic. No DB migration:
    `updated_at` and terminal-state handling already exist.
  - Developer (notification-service): scheduler in `adapter/in/scheduler/`, one outbound
    rest-client in `adapter/out/client/` with two methods (list stale, set status), both
    sending `X-Service-Key`; reuse the US-2 notifications table and US-3 email path. No new
    operations in `notification-service.yaml` (the scheduler is internal, not a REST endpoint).
  - DevOps: `JOBHUB_INTERNAL_SERVICE_KEY` already provisioned for application-service per
    ADR 0008; ensure notification-service has it and an app-service base URL.

## Alternatives considered

- **Reuse `PATCH /applications/{id}/status` with the user JWT** — rejected: notification-service
  has no user JWT and cannot impersonate the application owner; this is precisely the case the
  `/internal/*` + service-key pattern exists for (ADR 0008).
- **Add service-key auth as a second accepted credential on the existing user endpoint** —
  rejected: it overloads one operation with two auth models and two ownership semantics
  (owner-scoped vs system-wide), making the contract and the resource harder to reason about.
- **A dedicated `POST /internal/applications/{id}/ghost` with no body** — rejected: a narrow
  single-purpose verb is less reusable than a general status update, and the system may later
  need other system-driven transitions. The general `PUT` with the existing status enum is the
  smaller contract surface overall.
- **notification-service writes to the `applications` schema directly** — rejected: violates
  schema-per-service isolation, a core project constraint.
- **Event-driven (publish a "stale" event, application-service reacts)** — rejected as
  over-engineered for a daily batch of a few applications; REST with the established internal
  pattern is adequate.
