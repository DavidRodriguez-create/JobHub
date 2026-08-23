# notification-service

**Architecture:** [Hexagonal](../architecture/hexagonal.md) · **Schema:** `notification` ·
**Port:** 8084 · **Routed at:** `/notifications`

## Responsibility

Owns everything related to user notifications: per-type preference toggles, the in-app
notification center (bell icon + dropdown), the weekly digest email, interview reminders
(24h + 1h before a scheduled interview), and the ghosted-alert scheduler that auto-advances
silent applications to `GHOSTED` after 14 days of inactivity.

JWT **verify-only** (it trusts tokens signed by `auth-service`). For its outbound calls into
`auth-service`, `application-service` and `job-service`, it uses internal
`X-Service-Key`-guarded endpoints (pre-shared `JOBHUB_INTERNAL_SERVICE_KEY`) rather than a
user JWT.

## Endpoint groups

| Group | Paths | Purpose |
|-------|-------|---------|
| Preferences | `/notifications/preferences` | Read / upsert the four boolean preference toggles |
| Notification center | `/notifications`, `/notifications/unread-count`, `/notifications/{id}/read`, `/notifications/read-all` | Paginated list (newest first, `X-Total-Count`), unread badge count, mark-one-read, mark-all-read |

Pagination uses `page` (0-based), `size` (1..100, default 20) and `readStatus`
(`all` / `read` / `unread`).

## Scheduled jobs

| Job | Schedule (default) | What it does |
|-----|--------------------|--------------|
| Weekly digest | `0 0 8 ? * MON` | Per user with `weeklyDigestEmail = true`: build a digest of up to 10 new jobs matching their interest profile and any application updates; render the Qute template; send via the configured mailer. Persists a `digest_run` row per user/week for idempotency. |
| Interview reminders | configurable cron | Per user with `interviewReminders = true`: read the internal "upcoming next steps" endpoint from `application-service` and queue an in-app notification 24h and 1h before each scheduled interview. |
| Ghosted alert | `0 0 2 * * ?` (default 02:00 UTC daily) | Per user with `ghostedAlert = true`: call `GET /internal/applications/stale?days=14`, drive each stale application through `PUT /internal/applications/{id}/status` to advance it to `GHOSTED`, then create a `GHOSTED_ALERT` in-app notification (and email when the user's delivery preference allows it). |

All three jobs are guarded by an `enabled` config flag so they can be disabled per
environment without a redeploy.

## Outbound calls

`adapter/out/client/` is grouped by callee:

- `client/auth/` : `UserEmailGatewayAdapter` resolves a user UUID into the verified email
  address used by the mailer (internal `auth-service` endpoint).
- `client/application/` : `InterestProfileGatewayAdapter` fetches the user's interest profile
  for the digest; the ghosted-alert scheduler uses the stale-applications + status-update
  internal endpoints.
- `client/job/` : `JobSearchGatewayAdapter` runs the same job search the user would see, scoped
  to the digest window.

All three use Quarkus REST Client with `quarkus.rest-client.logging.scope=request-response`
and are tested with WireMock.

## Email delivery

`adapter/out/mail/QuteDigestMailer` renders the digest with Qute templates under
`src/main/resources/templates/` and dispatches via the Quarkus mailer extension. SMTP
endpoint, sender address and credentials come from `application-prod.properties` / `.env`
in compose; the digest tests use the in-memory mock mailer.

## Persistence

| Table | Purpose |
|-------|---------|
| `notification.notification_preferences` | One row per user; boolean toggles + audit timestamps (`040-notification.sql`) |
| `notification.notifications` | In-app notifications (id, user_id, type, title, message, read, created_at; later linked to source application via `application_id` from `044-notification-add-application-id.sql`) (`041-notification-notifications.sql`) |
| `notification.digest_run` | One row per user per weekly-digest run (`042-notification-digest-run.sql`); enforces "one digest per user per week" idempotency |
| `notification.interview_reminder_sent` | One row per `(application_id, next_step_date, reminder_offset)`; idempotency log so the 24h and 1h interview reminders fire exactly once (`043-notification-interview-reminders.sql`) |

Full contract: [API reference → notification-service](../api/notification.md).
