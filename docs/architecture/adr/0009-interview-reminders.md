# ADR 0009: Interview Reminders (24h + 1h): Idempotency, Internal Endpoint, Preference Shape

- **Status:** Proposed
- **Date:** 2026-06-15
- **Deciders:** Principal Architect (jobhub-architect); David R H
- **Affects:** notification-service, application-service, api-contracts, db/init

## Context

Story #81 (US 4, epic #96) adds interview reminders. A user with a scheduled next step (an
application carrying `next_step_label` + `next_step_date`) should be reminded 24 hours and 1
hour before that step. Each reminder is delivered as an in-app notification (US 2 notification
center) and, optionally, an email. Reminder content must include the next-step label
("Interview with Product Manager"), the date/time, and the company name. Reminders must be
gated by the `interview_reminders` preference and must never be sent twice.

The story owner clarified: "the user should have the ability to disable the notifications not
just choose between the two." So the preference model must support both (a) disabling interview
reminders entirely and (b) choosing the delivery channel(s).

This is the second scheduled, cross-service feature in notification-service, after the weekly
digest (ADR 0008). It reuses that ADR's established patterns: the `X-Service-Key` internal
auth, the hexagonal scheduler to use-case to outbound-gateway flow, and a per-run log table for
idempotency.

JobHub conventions in scope: Hexagonal architecture for notification-service (ADR 0007) and
application-service, contract-first api-contracts (interface-only generation), schema-per-service,
DB owned by `db/init/` SQL, no framework annotations below the adapter layer.

What already exists on the base that this design builds on:

- notification-service: `WeeklyDigestScheduler`, `WeeklyDigestService`, `DigestRunEntity` /
  `DigestRunRepository` / `DigestRunMapper` idempotency precedent, the `AppInternalRestClient`
  + `InterestProfileGatewayAdapter` outbound pattern, `NotificationPanacheRepository` /
  `NotificationEntity` for in-app delivery, `QuteDigestMailer` for email.
- `NotificationType.INTERVIEW_REMINDER` and `NotificationPreferences.interviewReminders`
  (boolean, column `interview_reminders`, default TRUE) already exist.
- application-service: the `InternalUserResource implements InternalApi` + `ServiceKeyFilter`
  service-to-service pattern, and the `next_step_label` (TEXT) / `next_step_date` (DATE) /
  `next_step_reminder_at` (TIMESTAMPTZ) columns on `applications.application`.

## Decision

### 1. Idempotency: a `notification.interview_reminder_sent` log table

We will add a forward-only log table keyed by `(user_id, application_id, offset)` with a UNIQUE
constraint, mirroring the `digest_run` precedent rather than putting boolean flags on the
application row.

```sql
notification.interview_reminder_sent (
    id              UUID PK DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,           -- references auth.user.id, no FK (cross-schema)
    application_id  UUID NOT NULL,           -- references applications.application.id, no FK
    reminder_offset TEXT NOT NULL,           -- 'H24' | 'H1'
    next_step_date  DATE NOT NULL,           -- the step date this reminder was for (audit / reschedule)
    channels        TEXT NOT NULL,           -- which channels fired, e.g. 'in_app' or 'in_app,email'
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_interview_reminder_offset CHECK (reminder_offset IN ('H24', 'H1')),
    CONSTRAINT uq_interview_reminder_sent UNIQUE (user_id, application_id, reminder_offset)
)
```

The scheduler inserts one row per (user, application, offset) the first time it fires that
reminder. The UNIQUE constraint is the hard guarantee against double-send: a second attempt
(retry, overlapping scheduler tick, restart) hits the constraint and is skipped. The service
checks existence before doing outbound work (in-app + email) and relies on the constraint as
the backstop, exactly as `WeeklyDigestService` checks `hasSentThisWeek(userId)` before working.

Why the UNIQUE key is `(user_id, application_id, reminder_offset)` and NOT date-scoped: a single
application has at most one open next step at a time, and the H24/H1 pair is one-shot per step.
If the user reschedules the step to a new date, that is a product decision recorded in
`next_step_date` for audit; re-arming reminders for a moved step is a follow-up (see
Consequences), not v1 behaviour. Keeping the key date-free makes "already reminded for this
step" unambiguous.

### 2. Internal endpoint: `GET /internal/applications/upcoming-next-steps`

We will add an internal, `X-Service-Key`-protected endpoint to application-service that returns
upcoming next steps across ALL users, following the interest-profile precedent
(`InternalUserResource implements InternalApi`, `@PermitAll`, `ServiceKeyFilter`).

- **Path / method:** `GET /internal/applications/upcoming-next-steps`
- **Query param:** `withinHours` (integer, 1 to 168, default 26): a forward window from now.
  The caller asks for a window generous enough to cover the largest reminder offset (24h) plus
  its own poll slack, then computes the exact 24h and 1h fire times locally. Default 26 = 24h
  offset + 2h scheduler slack.
- **Response (`UpcomingNextStepsResponse`):** `{ items: [UpcomingNextStepItem] }` where each
  item is `{ userId, applicationId, nextStepLabel, nextStepDate, nextStepReminderAt?,
  companyName?, status }`. The query selects applications with a non-empty `next_step_label`, a
  `next_step_date` within the window, and a non-terminal status, joined to the job snapshot /
  manual-entry job for `companyName`.
- Returns 200 with empty `items` when nothing is upcoming.

This puts the cross-user query in application-service (its data, its schema), keeping
schema-per-service isolation intact. It mirrors the rationale in ADR 0008 section 2 for the
interest-profile endpoint.

**No application-service schema change.** This is a read-only aggregation over existing
columns (`user_id`, `next_step_label`, `next_step_date`, `next_step_reminder_at`, `status`)
joined to the existing `job_post_snapshot` / `user_job_post` tables for the company name.
applications migration 031 is therefore **N/A** for this story.

`next_step_date` is a DATE, so the reminder "date/time" granularity is day-level unless
`next_step_reminder_at` is set. notification-service computes the H24/H1 fire instants from
`next_step_date` (treated as start-of-day in the deployment timezone) and may use
`next_step_reminder_at` when present. Choosing a precise time-of-day model for interviews is a
product/data follow-up and does not change this contract.

### 3. Preference shape: master disable + email channel choice

We will keep the existing `interviewReminders` boolean as the **master disable** and add a new
`interviewReminderEmail` boolean for the **email channel choice**. This directly satisfies the
owner's two requirements and stays consistent with how `weeklyDigestEmail` and
`inAppNotificationsEnabled` already model email-vs-in-app as independent booleans.

Semantics:

- `interviewReminders = false`: nothing is sent on any channel. This is "disable entirely".
- `interviewReminders = true`: an in-app notification is always created, and an email is sent
  **additionally** only if `interviewReminderEmail = true`. So in-app is the always-on channel
  when reminders are enabled, and email is the opt-in extra channel.

Defaults (backward-compatible): `interviewReminders` keeps its existing default `TRUE`; the new
`interviewReminderEmail` defaults `TRUE`. A user who never touched preferences gets in-app +
email reminders, matching the spec's intent that reminders are on by default. The new column is
`NOT NULL DEFAULT TRUE`, so existing preference rows backfill safely with no data migration of
values.

We reject an in-app toggle for interview reminders separate from the master switch: when
reminders are enabled, the in-app notification is the baseline, low-cost delivery and the
notification center is the canonical surface for US 2. "Disable in-app but keep email" is not a
requested mode and would add a third boolean for no product value. If the global
`inAppNotificationsEnabled` master is later wired to suppress all in-app surfaces, interview
reminders inherit that behaviour like any other in-app notification; that interaction is a
notification-service implementation detail, not a contract change.

This shape drives both contracts: the notification-service preferences response/request gain
`interviewReminderEmail`, and the entity/column `interview_reminder_email` is added (migration
043, below). The frontend preferences screen renders the master toggle plus an "also email me"
sub-toggle.

### 4. Scheduler cadence and window

A `@Scheduled` method in `adapter/in/scheduler/InterviewReminderScheduler` runs hourly
(`cron = "0 0 * * * ?"`, config key `notification.interview-reminder.cron`), gated by
`notification.interview-reminder.enabled` (default `true`), mirroring `WeeklyDigestScheduler`.

Per tick the scheduler:

1. Calls `GET /internal/applications/upcoming-next-steps?withinHours=26` (one batched
   cross-user call, like the digest's batched email fetch).
2. Loads local `notification_preferences` for the returned user IDs and drops users with
   `interview_reminders = false`.
3. For each remaining (user, application): computes the H24 and H1 fire instants from
   `next_step_date` (and `next_step_reminder_at` when present). For each offset whose fire
   instant is at or before now (within the current window) and not already logged in
   `interview_reminder_sent`, it creates the in-app notification, optionally sends the email
   (if `interview_reminder_email = true`), then inserts the `interview_reminder_sent` row.
4. Records failures per (user, application, offset) and continues, as the digest service does.

Hourly cadence with a 26h lookahead means each reminder offset has up to an hour of slack
without missing or duplicating: idempotency comes from the log table, not from precise timing,
so an hourly tick that overlaps a previous slow run is safe. Email addresses are resolved via
the existing `UserEmailGateway` (auth-service `/internal/users/emails`) only for users who will
actually receive an email.

### 5. notification-service hexagonal additions (shapes only, for the developer)

```
domain/
  model/
    UpcomingNextStep            -- userId, applicationId, label, stepDate, reminderAt?, company?, status
    ReminderOffset             -- enum H24, H1
    InterviewReminderSent      -- userId, applicationId, offset, stepDate, channels, sentAt
  port/
    in/
      SendInterviewRemindersUseCase   -- triggered by scheduler (run())
    out/
      UpcomingNextStepsGateway        -- fetch upcoming steps from application-service
      InterviewReminderSentRepository -- exists()/save() against the log table
      (reuse) UserEmailGateway, NotificationRepository, NotificationPreferencesRepository,
              DigestMailer-style InterviewReminderMailer (new Qute template) or extend mail port
  service/
    InterviewReminderService   -- implements the use case, orchestrates the flow
adapter/
  in/scheduler/InterviewReminderScheduler
  out/
    client/application/        -- add getUpcomingNextSteps to AppInternalRestClient + a gateway adapter
    persistence/entity/InterviewReminderSentEntity, mapper/InterviewReminderSentMapper,
                InterviewReminderSentPanacheRepository
    mail/InterviewReminderMailer (+ templates/interview-reminder-email.html)
```

`NotificationPreferences` (domain + entity) gains `interviewReminderEmail`. No framework
annotations below the adapter layer; gateways depend on generated contract models only at the
adapter boundary, as `InterestProfileGatewayAdapter` does.

### 6. Migration-number ranges

| Ticket | Service | File | Purpose |
|--------|---------|------|---------|
| #105 follow-up (dev) | notification-service | `db/init/043-notification-interview-reminders.sql` | `interview_reminder_sent` table AND `ALTER TABLE notification.notification_preferences ADD COLUMN interview_reminder_email BOOLEAN NOT NULL DEFAULT TRUE` |
| #105 follow-up (dev) | application-service | (none) | **N/A** (read-only query over existing columns, no schema change) |

notification = 043 is the next free number after 040/041/042. Both notification changes (new
table + new preferences column) live in the single 043 file since they are one story's
notification-schema delta. On an existing data volume this is a forward-only file that must be
applied by hand per CLAUDE.md ("Running the full stack in Podman").

### 7. Config keys (notification-service)

| Key | Default | Purpose |
|-----|---------|---------|
| `notification.interview-reminder.enabled` | `true` | Kill switch for the scheduler |
| `notification.interview-reminder.cron` | `0 0 * * * ?` | Hourly cron (overridable) |
| `notification.interview-reminder.within-hours` | `26` | Lookahead window passed to the internal endpoint |

Reuses the existing `notification.internal.service-key`, `quarkus.rest-client.app-internal.url`,
and `quarkus.rest-client.auth-internal.url` from ADR 0008. application-service reuses its
existing `ServiceKeyFilter` and key config; no new config there.

### 8. Test shape

| Layer | What | How |
|-------|------|-----|
| Unit: `InterviewReminderService` | Computes H24/H1, skips disabled users, skips already-sent, chooses channels | Mockito, mock all outbound ports |
| Unit: gateway adapter | Maps `UpcomingNextStepsResponse` to domain | Mockito, mock RestClient |
| Unit: `InterviewReminderSentMapper` | Entity/domain mapping | Plain JUnit |
| Component (notification): `InterviewReminderScheduler` | End-to-end with WireMock for app/auth-service, DevServices DB, mock mailer; assert UNIQUE prevents double-send | `@QuarkusTest` + WireMock |
| Component (application): internal endpoint | `X-Service-Key` validation, window filtering, terminal-status exclusion, company resolution | `@QuarkusTest` + DevServices |

## Consequences

- **Positive:** The log-table idempotency reuses the proven `digest_run` pattern and keeps the
  hard guarantee (UNIQUE constraint) in the database, not in scheduler timing. The H24/H1 are
  independent rows, so one offset can succeed while the other retries.
- **Positive:** Keeping the cross-user query in application-service preserves schema-per-service
  isolation; notification-service never reads the `applications` schema directly.
- **Positive:** The preference shape is a minimal, backward-compatible extension (one new
  boolean, default TRUE) that satisfies both "disable entirely" and "choose channel" without a
  data migration of existing values.
- **Negative / cost:** A new outbound operation, a new Qute template, a new entity/repository,
  and a new scheduler in notification-service; component tests need WireMock (already present
  since ADR 0008).
- **Negative / cost:** `next_step_date` is day-granular, so without `next_step_reminder_at` the
  "1h before" reminder is approximate. Acceptable for v1; a precise interview time-of-day is a
  follow-up.
- **Follow-ups:**
  - Developer: write `db/init/043-notification-interview-reminders.sql` (table + column), the
    notification-service skeleton in section 5, and the application-service internal endpoint /
    query.
  - Rescheduling: re-arming reminders when a user moves `next_step_date` is out of scope; if
    desired later, change the UNIQUE key to include `next_step_date` or clear prior log rows on
    reschedule. Record in a future ADR.
  - DevOps: no new env vars beyond ADR 0008's `JOBHUB_INTERNAL_SERVICE_KEY` and the SMTP config
    already required by the digest.
  - Frontend (PDA): preferences UI gains the "also email me" sub-toggle under the interview
    reminders master switch; default both on.

## Alternatives considered

- **Boolean flags on the application row (`h24_reminder_sent`, `h1_reminder_sent`)**: rejected.
  It would force a schema change in application-service (breaking the "no app migration" result),
  put notification-service's delivery state inside another service's bounded context, and lose
  the audit trail (when/what channel) that a log table gives. The `digest_run` precedent already
  favours a log table.
- **Date-scoped idempotency key `(user_id, application_id, offset, next_step_date)`**: rejected
  for v1 because it complicates "already reminded for this step" and invites duplicate reminders
  if a step's date is edited mid-flight. Rescheduling is an explicit follow-up.
- **A third in-app channel toggle for interview reminders**: rejected. When reminders are
  enabled, in-app is the baseline surface (US 2 notification center). A separate "disable in-app
  but keep email" mode was not requested and adds a boolean for no product value.
- **Per-user calls to fetch upcoming steps**: rejected. A single cross-user windowed query is
  far cheaper than N per-user calls and matches the digest's batched-fetch approach. The window
  bounds the result size.
- **Push reminder fan-out from application-service (event on next-step set)**: rejected as
  over-engineered: it needs scheduled delivery and retry/idempotency state somewhere regardless,
  and JobHub has no message bus. A polling scheduler with a log table is adequate for the scale.
