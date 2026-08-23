# ADR 0008: Weekly Digest Email — Cross-Service Communication & Design

- **Status:** Proposed
- **Date:** 2026-06-15
- **Deciders:** Principal Architect (jobhub-architect); David R H
- **Affects:** notification-service, application-service, auth-service, api-contracts, db/init

## Context

Story #80 (epic #96) adds a weekly digest email to notification-service. Every Monday morning
a scheduled job finds users who have `weekly_digest_email = true`, determines what jobs to
recommend, and sends an HTML email. This is the first feature in notification-service that
requires **outbound REST calls** to other services and **email dispatch**, neither of which
exist in the service today.

The design must resolve several cross-cutting concerns:

1. **Service-to-service authentication.** notification-service runs a scheduler, not a
   user-initiated request. It does not possess user JWTs and cannot impersonate users. The
   internal endpoints it calls must accept a different credential.

2. **Interest profile extraction.** application-service stores each user's application
   history with frozen job snapshots (title, company, location). To recommend jobs, we need
   to aggregate the titles, locations, and companies the user has applied to. application-
   service's existing `GET /applications` endpoint is per-user/JWT-scoped and not callable
   by notification-service.

3. **User email lookup.** auth-service stores user emails but has no endpoint to look them up
   by user ID. notification-service needs the email address for each opted-in user.

4. **Email sending.** Quarkus Mailer with Qute templates is the Quarkus-standard approach.

5. **Digest tracking.** We need to know when the last digest was sent per user (to avoid
   double-sends on retries, and to scope "recent jobs" relative to the last digest).

JobHub conventions in scope: Hexagonal architecture for notification-service (ADR 0007),
contract-first api-contracts, schema-per-service, DB owned by `db/init/` SQL.

## Decision

### 1. Service-to-service authentication: shared secret header

We will use a **pre-shared API key** exchanged via config/environment variable. Each internal
endpoint checks for a header `X-Service-Key` whose value must match the deployment secret
(`JOBHUB_INTERNAL_SERVICE_KEY` env var). This is simple, adequate for services on the same
Podman network, and requires no token-minting infrastructure.

- Internal endpoints are tagged `Internal` in OpenAPI and document the `X-Service-Key`
  header requirement.
- The key is a single shared value across all services (one `.env` variable). Any service
  that exposes or calls internal endpoints reads it from config.
- Internal endpoints return 401 if the header is missing or wrong.
- This is NOT a user-identity mechanism. Internal endpoints that need a user context receive
  the user ID as a path or query parameter, not from a JWT.

### 2. New internal endpoints

**auth-service** -- `GET /internal/users/emails` (batch):

- Accepts `userIds` as a repeated query parameter (list of UUIDs).
- Returns `{ emails: [ { userId, email } ] }`. Users not found are silently omitted.
- Protected by `X-Service-Key`. No JWT required.
- No schema change needed (reads existing `auth.user` table).

**application-service** -- `GET /internal/users/{userId}/interest-profile`:

- Returns an aggregated interest profile for one user, derived from their application
  history: the top locations, top companies, and top title keywords extracted from
  `applications.job_post_snapshot` and `applications.user_job_post` joined through
  `applications.application`.
- Response: `{ userId, locations: [string], companies: [string], keywords: [string] }`.
  Each array is at most 5 entries, ordered by frequency.
- Returns 200 with empty arrays if the user has no application history (the caller
  decides whether to send a generic digest or skip).
- Protected by `X-Service-Key`. No JWT required.
- No schema change needed (reads existing tables via a GROUP BY/COUNT query).

### 3. Interest profile to job search mapping

notification-service builds a job-service `GET /jobs` query from the interest profile:

- `keyword` = top 3 title keywords joined by spaces (OR semantics in full-text search).
- `location` = all profile locations (repeated query param).
- `postedWithin` = `week` (only jobs seen in the last 7 days).
- `sort` = `newest`, `size` = 10.

For users with **no application history** (empty interest profile): send a generic "top jobs
this week" digest using `GET /jobs?postedWithin=week&sort=newest&size=10` with no keyword or
location filter. Users with no history still receive the digest if they opted in -- we do not
skip them.

### 4. Email sending: Quarkus Mailer + Qute templates

notification-service adds `quarkus-mailer` and `quarkus-qute` dependencies. The digest email
is rendered from a Qute template (`templates/digest-email.html`) containing job cards with
title, company, location, and a link. SMTP config is via standard Quarkus Mailer properties
(`quarkus.mailer.*`), with Mailtrap/MailHog for dev.

### 5. Digest run tracking: `notification.digest_run` table

A new table `notification.digest_run` records each digest execution:

```sql
notification.digest_run (
    id              UUID PK,
    user_id         UUID NOT NULL,
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    job_count       INTEGER NOT NULL,
    status          TEXT NOT NULL CHECK (status IN ('sent', 'failed', 'skipped')),
    error_message   TEXT
)
```

This enables:
- Preventing double-sends on retries (check if a `sent` row exists for this user in the
  current week).
- Scoping "recent" to "since last successful digest" if we want to in the future.
- Observability (how many digests sent, failure rate).

Migration file: `db/init/042-notification-digest-run.sql` (041 was taken by story #79).

### 6. Scheduler design

A `@Scheduled` method in `adapter/in/scheduler/WeeklyDigestScheduler` fires every Monday at
08:00 UTC (`cron = "0 0 8 ? * MON"`). Config key `notification.digest.enabled` (default
`true`) gates execution so it can be disabled without redeployment.

The scheduler:
1. Queries all users with `weekly_digest_email = true` from the local
   `notification_preferences` table.
2. Batch-fetches emails from auth-service (`GET /internal/users/emails`).
3. For each user, fetches interest profile from application-service, builds a job-service
   query, fetches matching jobs.
4. Renders the Qute email template and sends via Quarkus Mailer.
5. Records the result in `digest_run`.

Processing is sequential per user (simplicity first). If a per-user step fails, log the error,
record a `failed` digest_run row, and continue to the next user. The batch-email fetch in step
2 is the only batched call; steps 3-5 are per-user.

### 7. notification-service hexagonal structure additions

```
domain/
  model/
    InterestProfile            -- locations, companies, keywords (from app-service)
    DigestRun                  -- id, userId, sentAt, jobCount, status, errorMessage
    UserEmail                  -- userId, email (from auth-service)
  port/
    in/
      SendWeeklyDigestUseCase  -- triggered by scheduler
    out/
      DigestRunRepository      -- persist/query digest runs
      InterestProfileGateway   -- fetch interest profile from application-service
      UserEmailGateway         -- fetch user emails from auth-service
      JobSearchGateway         -- search jobs from job-service
      DigestMailer             -- send the rendered email
  service/
    WeeklyDigestService        -- implements SendWeeklyDigestUseCase, orchestrates the flow
  exception/
    DigestSendException        -- wraps mailer failures

adapter/
  in/
    scheduler/
      WeeklyDigestScheduler    -- @Scheduled, calls SendWeeklyDigestUseCase
  out/
    client/
      auth/
        AuthInternalRestClient       -- @RegisterRestClient, X-Service-Key header
        UserEmailGatewayAdapter      -- implements UserEmailGateway
      application/
        AppInternalRestClient        -- @RegisterRestClient, X-Service-Key header
        InterestProfileGatewayAdapter -- implements InterestProfileGateway
      job/
        JobServiceRestClient         -- @RegisterRestClient (public, no auth needed)
        JobSearchGatewayAdapter      -- implements JobSearchGateway
    persistence/
      entity/DigestRunEntity
      mapper/DigestRunMapper
      DigestRunPanacheRepository
    mail/
      QuteDigestMailer               -- implements DigestMailer port
```

### 8. Migration-number ranges

| Ticket | Service | File | Purpose |
|--------|---------|------|---------|
| #99 | notification-service | `db/init/042-notification-digest-run.sql` | `digest_run` table |
| #100 | auth-service | (none) | No schema change; new REST endpoint over existing `auth.user` |
| #101 | application-service | (none) | No schema change; new REST endpoint with GROUP BY query |

### 9. Config keys (notification-service)

| Key | Default | Purpose |
|-----|---------|---------|
| `notification.digest.enabled` | `true` | Kill switch for the scheduler |
| `notification.digest.cron` | `0 0 8 ? * MON` | Cron expression (overridable) |
| `notification.digest.max-jobs` | `10` | Max jobs per digest email |
| `notification.internal.service-key` | (no default, required in prod) | Shared secret for internal endpoints |
| `quarkus.rest-client.auth-internal.url` | `http://localhost:8082` | auth-service base URL |
| `quarkus.rest-client.app-internal.url` | `http://localhost:8083` | application-service base URL |
| `quarkus.rest-client.job-service.url` | `http://localhost:8081` | job-service base URL |

auth-service and application-service each read the same key for validation:
`jobhub.internal.service-key` (from `JOBHUB_INTERNAL_SERVICE_KEY` env var).

### 10. Test shape

| Layer | What | How |
|-------|------|-----|
| Unit: `WeeklyDigestService` | Orchestration logic: builds queries, handles empty profiles, records runs | Mockito, mock all outbound ports |
| Unit: `InterestProfileGatewayAdapter` | Maps REST response to domain model | Mockito, mock RestClient |
| Unit: `UserEmailGatewayAdapter` | Maps batch response | Mockito |
| Unit: `DigestRunMapper` | Entity/domain mapping | Plain JUnit |
| Component: `WeeklyDigestScheduler` | End-to-end with WireMock for auth/app/job-service, DevServices DB, mock Mailer | `@QuarkusTest` + WireMock |
| Component (auth-service): internal endpoint | X-Service-Key validation, batch lookup | `@QuarkusTest` + DevServices |
| Component (app-service): internal endpoint | X-Service-Key validation, profile aggregation | `@QuarkusTest` + DevServices |

notification-service will need WireMock for the first time (three outbound HTTP clients).

## Consequences

- **Positive:** The internal-endpoint pattern with `X-Service-Key` is simple, explicit, and
  reusable for future service-to-service calls (e.g. ghosted-alert checking). No new
  infrastructure (no service mesh, no mTLS, no token-exchange).
- **Positive:** Interest profile extraction stays in application-service (its data, its
  query), keeping schema boundaries intact.
- **Positive:** Users with no history still get a digest (generic "top jobs"), maximising
  engagement for new users who opted in.
- **Negative / cost:** The shared secret is a single point of compromise. Acceptable for a
  same-network deployment; a future ADR can upgrade to mTLS or service-to-service JWT if the
  threat model changes.
- **Negative / cost:** notification-service now has three outbound REST clients and needs
  WireMock, increasing test complexity. Acceptable -- application-service already follows
  this pattern.
- **Follow-ups:**
  - DevOps: add `JOBHUB_INTERNAL_SERVICE_KEY` to `.env.example` and the Podman compose env
    for auth-service, application-service, and notification-service.
  - DevOps: add `quarkus-mailer` SMTP config to notification-service's Podman env.
  - Future: the `X-Service-Key` pattern should be extracted to a shared JAX-RS
    `ContainerRequestFilter` in a common library if more than two services expose internal
    endpoints.

## Alternatives considered

- **Service-to-service JWT (notification-service mints its own token)** -- rejected because
  it requires a signing key in notification-service and a new issuer trust chain in auth-
  service. Over-engineered for same-network services with no internet exposure.
- **Direct database cross-schema reads** -- rejected because it violates schema-per-service
  isolation (a core project constraint). notification-service must not read from `auth` or
  `applications` schemas.
- **Skip users with no application history** -- rejected because new users who opt in to
  the digest should still receive value. A generic "top jobs" digest is low-cost and
  high-engagement.
- **Event-driven (Kafka/AMQP) instead of REST for interest profiles** -- rejected as
  over-engineered for a weekly batch of a few hundred users at most. REST with a simple
  aggregation query is adequate.
- **Batch all interest profile fetches into one call** -- considered but rejected for v1.
  The per-user call is simpler and the user count is small. If scale demands it, a batch
  endpoint can be added later without changing the port interface contract.
