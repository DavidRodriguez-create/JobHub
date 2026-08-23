# ADR 0007: Notification Service — Architecture & Preferences API

- **Status:** Accepted
- **Date:** 2026-06-13
- **Deciders:** Principal Architect (jobhub-architect); David R H
- **Affects:** notification-service (new), api-contracts, db/init, db/init-users.sh, parent pom.xml

## Context

Feature #59 introduces a notification subsystem with four notification types (weekly digest
email, in-app notifications, interview reminders, ghosted alert). Story #78 is the scaffold
and preferences API freeze. The notification feature owns:

1. **User preferences** — per-type on/off toggles controlling delivery channels.
2. **Future: notification storage & delivery** — in-app notification list, email dispatch,
   scheduling logic for reminders and digest.

The domain is primarily *technical*: preferences are a simple CRUD with no invariants beyond
"one row per user with boolean toggles." Future notification delivery is also mechanistic
(schedule, check preference, dispatch). There is no rich entity behaviour or complex business
rules that would warrant Clean Architecture.

The existing UI (`SavedSettings.jsx`) already renders the four toggles but they are
frontend-only state — this service provides the persistent backend.

Locked decisions from the feature spec:
- New `notification-service`, port 8084.
- `notification` DB schema, `notification_user` least-privilege account.
- In-app notifications only (NO Web Push / Service Worker).
- Delivery channels: in-app + email (chosen per type via preferences).
- Migration range: `db/init/040-049`.

## Decision

We will create `notification-service` as a **Hexagonal (Ports & Adapters)** service, following
the same layering as `job-service`:

```
domain/
  model/NotificationPreferences         — immutable @Getter @Builder, no framework annotations
  port/in/GetPreferencesUseCase         — returns preferences for a user
  port/in/UpdatePreferencesUseCase      — upserts preferences with partial-update semantics
  port/out/NotificationPreferencesRepository
  service/NotificationPreferencesService — implements both in-ports, injects repository port
  exception/PreferencesNotFoundException

adapter/
  in/rest/NotificationPreferencesResource — implements generated NotificationsApi
  in/rest/dto/NotificationPreferencesResponseMapper
  in/rest/exception/GenericExceptionMapper
  out/persistence/entity/NotificationPreferencesEntity
  out/persistence/mapper/NotificationPreferencesMapper
  out/persistence/NotificationPreferencesPanacheRepository
```

**API contract** (frozen in `api-contracts/openapi/notification-service.yaml`):

| Method | Path | Description |
|--------|------|-------------|
| GET | `/notifications/preferences` | Return the authenticated user's preference toggles (defaults if no row) |
| PUT | `/notifications/preferences` | Upsert with partial-update semantics |

Both endpoints require a Bearer token (401 if absent). User ID is derived from the JWT
subject claim, same pattern as job-service and application-service.

**Database** (`db/init/040-notification.sql`):

- `notification.notification_preferences` — one row per user (UUID PK, user_id unique,
  4 boolean columns with defaults, created_at, updated_at + trigger).
- Schema and user created in `001-schemas.sql` and `init-users.sh`.

**Auth integration:**

- JWT verification only (no token minting). `quarkus-smallrye-jwt` + shared dev keypair
  via the parent gmavenplus plugin (same pattern as job-service/application-service).
- No outbound REST clients needed for the preferences API (no WireMock in tests).

## Consequences

- **Positive:** Notification preferences are served from a dedicated, single-responsibility
  service with its own schema — no coupling to auth-service or application-service internals.
  The contract is frozen and the UI can be wired immediately.
- **Positive:** Future notification delivery (in-app list, email dispatch, reminders) lives in
  the same service, extending the 040-049 migration range and the existing hexagonal structure.
- **Negative / cost:** A fifth JVM service in the stack increases local-dev memory pressure
  and Podman resource usage. Acceptable given the clear bounded context.
- **Follow-ups:**
  - DevOps: add `notification-service` entry to `podman-compose.yml` (port 8084, DB credentials
    from `.env`, depends on `jobhub-db`). Add `NOTIFICATION_PASSWORD` to `.env.example`.
  - DevOps: update Vite proxy config and nginx routing for `/notifications/` if the UI
    needs direct access (currently it only uses the preferences endpoint).
  - Future stories: `041-notification-items.sql` (in-app notification storage), email
    templates, scheduler adapter, possibly outbound REST client to application-service for
    ghosted-alert detection.

## Alternatives considered

- **Embed preferences in auth-service** — rejected because auth-service uses Clean
  Architecture for its domain-rich identity model; notification preferences are a separate
  bounded context with different lifecycle and no entity behaviour. Adding them to auth
  would blur the service boundary.
- **Embed preferences in application-service** — rejected because notification preferences
  apply to all notification types (not just application-related ones), and the notification
  feature will grow to include delivery scheduling which is orthogonal to application tracking.
- **Clean Architecture for notification-service** — rejected because the domain is simple
  (boolean toggles, no invariants, no rich entity behaviour). The Hexagonal rubric applies:
  few mechanistic use cases, REST/persistence are the main complexity.
