# ADR 0011: Custom reminders in notification-service (story #134) and the #153 channel-gating fix

- **Status:** Accepted
- **Date:** 2026-06-20
- **Deciders:** jobhub-architect (story #134, sub-issue #154); David R H
- **Affects:** notification-service, api-contracts, db/init, and (via the #153 root-cause) the existing preferences PUT path

## Context

Story #134 lets a job seeker schedule their own future-dated reminders on a specific
application (screening / interview / offer hint), with channel choice (in-app and/or email),
that fire once at the chosen time on top of the system's default reminders. This sits
alongside the existing notification-service capabilities: the weekly digest (ADR 0008), the
24h+1h interview reminders (ADR 0009), the ghosted-alert scheduler (the other 0009), and
the in-app notification center.

JobHub conventions in scope: Hexagonal architecture for notification-service (ADR 0007),
contract-first api-contracts (interface-only generation), schema-per-service, DB owned by
`db/init/` SQL, no framework annotations below the adapter layer, and the existing
internal-call patterns (auth-service `/internal/users/emails`, application-service
`/internal/applications/...`) when the scheduler needs cross-service data.

Bug #153 ("Toogle to enable email for interview reminder not working") is on the same
preferences write/read code path that this story extends, and the architect ticket #154
asked us to root-cause it and decide whether to fold the fix into backend ticket #157.

What already exists that this design builds on:

- `NotificationPreferences` (domain + entity + mapper + Panache repo) carrying both
  `interviewReminders` (master) and `interviewReminderEmail` (channel sub-pref) plus the
  three other toggles.
- `NotificationRepository` / `NotificationEntity` for in-app delivery (already has an
  optional `application_id` column from migration 044, story #82).
- The `@Scheduled` to use-case to outbound-port flow used by `WeeklyDigestScheduler`,
  `InterviewReminderScheduler` and `GhostedAlertScheduler`, with a per-run log table for
  idempotency (`digest_run`, `interview_reminder_sent`).
- The `QuteReminderMailer` / `QuteAlertMailer` / `QuteDigestMailer` outbound mail adapters
  using Qute templates with the shared notification email styling (story #62).
- Application ownership is already verified by `application-service`; the only new
  cross-service question is "does this application belong to this user".

## Decision

### 1. Architecture: Hexagonal (same as the rest of notification-service)

We will keep notification-service hexagonal (ADR 0007). No blended layers. Custom reminders
are a small set of mechanistic use cases (create, edit, cancel, list, dispatch) with a
near 1:1 mapping between REST endpoints and use cases, plus one scheduled dispatch loop.
The technical-service rubric (CLAUDE.md decision guide) clearly picks Hex over Clean.

### 2. Domain shape (Layer 1)

```
domain/model/
  CustomReminder           -- @Getter @Builder, immutable
                              id (UUID), userId (UUID), applicationId (UUID),
                              title (String, 1..200), note (String?, 0..2000),
                              triggerAtUtc (Instant), channels (Set<CustomReminderChannel>),
                              stage (CustomReminderStage?), status (CustomReminderStatus),
                              createdAt (Instant), updatedAt (Instant)
  CustomReminderChannel    -- enum IN_APP, EMAIL
  CustomReminderStage      -- enum SCREENING, INTERVIEW, OFFER
  CustomReminderStatus     -- enum SCHEDULED, FIRED, CANCELLED

domain/exception/
  CustomReminderNotFoundException        -- 404 (also used for non-owner)
  CustomReminderNotScheduledException    -- 409 (edit/cancel of FIRED or CANCELLED)
  CustomReminderTriggerInPastException   -- 400 (create/edit with non-future trigger)
  CustomReminderInvalidChannelsException -- 400 (empty channels after normalisation)
  ApplicationNotOwnedException           -- 404 on create when application is not the user's
```

Domain invariants enforced in the model (not at the REST adapter):
- `triggerAtUtc` strictly in the future at creation/edit time (validated against an injected
  `Clock`).
- `channels` non-empty after de-duplication (normalised to a `Set<CustomReminderChannel>`).
- `title` non-blank, length 1..200; `note` length 0..2000 when present.

Owner-scoping is enforced at the use-case layer: every read/write loads the row by
`(id, userId)` and treats "found by id but wrong user" identically to "not found" (404),
matching the notification-center precedent and avoiding existence leaks.

### 3. Ports (Layer 2)

```
domain/port/in/
  CreateCustomReminderUseCase    -- create(userId, command) -> CustomReminder
  UpdateCustomReminderUseCase    -- update(userId, id, command) -> CustomReminder
  CancelCustomReminderUseCase    -- cancel(userId, id) -> void
  GetCustomReminderUseCase       -- get(userId, id) -> CustomReminder
  ListMyCustomRemindersUseCase   -- list(userId, includeFired) -> List<CustomReminder>
  ListCustomRemindersByApplicationUseCase
                                 -- list(userId, applicationId, includeFired) -> List<CustomReminder>
  DispatchDueCustomRemindersUseCase -- run() -> void  (scheduler entry)

domain/port/out/
  CustomReminderRepository       -- save, update, findByIdForUser, findAllForUser,
                                    findAllForUserAndApplication, findDue(now, limit),
                                    markFired(id, channelsActuallyFired, firedAt)
  ApplicationOwnershipGateway    -- isOwnedByUser(applicationId, userId) -> boolean
                                    (calls application-service internal endpoint, see section 7)
  CustomReminderMailer           -- send(toEmail, reminder)
                                    (new Qute template; reuses notification-email base styling)
  (reuse) NotificationRepository                 -- to write the IN_APP delivery row
  (reuse) NotificationPreferencesRepository      -- to read master gates at dispatch time
  (reuse) UserEmailGateway                       -- batched email resolution for due reminders
```

### 4. Channel gating rule (locked, applies to dispatch only)

**Decision: master "email me" preferences GATE custom reminders. No override.**

At dispatch time, for each due reminder owned by user U asking for channels C:

- If C contains `EMAIL` and `NotificationPreferences.interviewReminderEmail` is `false`,
  the EMAIL channel is **dropped silently** for this firing. (Until story #135's follow-up
  adds a dedicated "custom-reminder email" flag, `interviewReminderEmail` is the master
  email gate the UI already exposes as "Also email me for alerts"; ADR 0010 already binds
  that copy to this flag.)
- If C contains `IN_APP` it is always honoured. There is no master "in-app" gate today;
  `inAppNotificationsEnabled` is inert (ADR 0010), so we do NOT consult it. If a later
  story wires it up, custom-reminder in-app delivery inherits that behaviour like any
  other in-app notification, no contract change needed.
- If C contains `EMAIL` but no email can be resolved for the user (auth-service down or
  user has no verified email), the EMAIL channel is dropped and the failure is logged but
  the reminder is still considered FIRED for its in-app side (mirrors the interview
  reminder behaviour).
- If, after gating, the effective channel set is empty (user asked email-only and email is
  gated off), the reminder is still marked FIRED to avoid an infinite retry loop, and a
  WARN log records that the user gated themselves out. The audit row records
  `channels_fired = ''` so this case is queryable.

Rationale: this is the **fall-through-to-master** rule, not the **override-master** rule.
The product story explicitly says "the user receives the chosen notification(s) ... gated
by the user's master notification preferences"; treating a custom reminder as a hard
override of a user-set "don't email me" toggle would surprise users and contradict the
master-toggle contract. The trade-off (a user might create an email reminder, then
disable master email, then forget the reminder won't email) is acceptable for v1 and is
exactly the same contract every other notification on the platform follows.

Future expansion: if product later wants per-reminder override semantics, add a
`bypassChannelGates: boolean` to the request/response and a new ADR; do NOT change the
default.

**Editing semantics:** at create/edit time we do NOT validate against gates. A user may
legitimately ask for EMAIL while currently having it disabled, intending to flip it on
later. Gating happens at dispatch only.

### 5. Use cases and the REST adapter (Layer 3 in)

The REST resource implements the generated `CustomRemindersApi` (api-contracts) under the
existing `NotificationResource`'s package, either as a sibling `CustomReminderResource`
(preferred for cohesion) or merged into `NotificationResource`. **Decision: new
`CustomReminderResource` class.** Keeps the existing resource focused on preferences +
notification center; matches the way `InterviewReminderScheduler` lives next to
`WeeklyDigestScheduler` rather than inside one mega-class.

**Implementation note (added 2026-06-21 during P3 conformance):** the
notification-service generator config in `api-contracts/pom.xml` does **not** set
`useTags=true`, so `openapi-generator-maven-plugin` groups operations by path prefix:
every operation under `/notifications/*` lands on a single `NotificationsApi`
interface (preferences + notification center + custom-reminder CRUD), and only the
by-application listing under `/applications/*` gets its own `ApplicationsApi`.
Honouring the generated interface therefore forces the custom-reminder CRUD onto the
class that implements `NotificationsApi` (i.e. `NotificationResource`); the
`CustomReminderResource` that does exist implements `ApplicationsApi` for the
by-application listing. The contract is the boundary, not the resource-class split, so
this is accepted as conformant. If a future story needs the cleaner per-tag class
split, switch this service's generator to `useTags=true` in a dedicated change (it
would rename the generated interfaces and reshape every resource in the service, so
not a stealth change).

```
adapter/in/rest/
  CustomReminderResource                 implements contract.api.CustomRemindersApi
    @Path is taken from the generated interface (/notifications/custom-reminders and
    /applications/{applicationId}/custom-reminders).
    @RolesAllowed("user"); JWT subject -> userId (UUID).
    Constructor-injects the seven use cases above plus the response mapper.
  dto/CustomReminderResponseMapper       static domain -> contract.CustomReminderResponse
  dto/CustomReminderListMapper           static List<domain> -> contract.CustomReminderList
  exception/
    CustomReminderNotFoundExceptionMapper            -> 404
    CustomReminderNotScheduledExceptionMapper        -> 409
    CustomReminderTriggerInPastExceptionMapper       -> 400
    CustomReminderInvalidChannelsExceptionMapper     -> 400
    ApplicationNotOwnedExceptionMapper               -> 404
```

The resource takes the contract request DTOs (`CreateCustomReminderRequest`,
`UpdateCustomReminderRequest`), maps them to a small in-package command record (immutable),
calls the use case, and maps the returned domain back to the contract response. The
existing `GenericExceptionMapper` is the fallback.

### 6. Scheduler integration

A new `CustomReminderDispatchScheduler` under `adapter/in/scheduler/` runs frequently and
calls `DispatchDueCustomRemindersUseCase.run()`. Mirrors the existing scheduler shape
(kill-switch config key + cron + try/catch).

- Default cron: every minute (`0 * * * * ?`), config key
  `notification.custom-reminder.cron`. One minute is the **delivery tolerance** the user
  perceives ("at the trigger time +/- tolerance").
- Kill switch: `notification.custom-reminder.enabled` (default `true`).
- Tolerance window: the use case loads reminders with `status = SCHEDULED AND
  trigger_at_utc <= now()`, ordered by `trigger_at_utc` ASC, with a batch size config
  `notification.custom-reminder.batch-size` (default `200`). No upper-bound check on
  "how late": if the scheduler was down for a day, all due reminders still fire on the
  next tick (catch-up). This is the same behaviour as the digest/interview/ghosted jobs.
- Idempotency: the row's `status` is the truth. The scheduler reads SCHEDULED rows,
  attempts dispatch, then flips to FIRED with `fired_at_utc = now()` and
  `channels_fired` recording what actually went out. A second concurrent scheduler tick
  cannot re-fire because the UPDATE is conditional (`UPDATE ... WHERE id = ? AND status
  = 'SCHEDULED'`); zero rows updated means another tick won the race and we skip.
- Per-reminder failure isolation: each reminder is processed in its own try/catch so one
  failure doesn't abort the batch (same shape as the interview scheduler's
  `processItem` loop).
- Email-resolution batching: collect distinct `userId`s for the batch, call
  `userEmailGateway.fetchEmails(userIds)` once, then dispatch.

There is no need for a separate `custom_reminder_fired` log table: unlike the
interview-reminder case where one application has multiple offsets (H24+H1) keyed
independently, a custom reminder is one-shot, so the row's own status IS the log.

### 7. Application ownership check (cross-service call)

To honour "a user cannot create a reminder on an application that isn't theirs", the
create use case must verify ownership. application-service already has an internal
endpoint pattern (`/internal/...` + `X-Service-Key`, ADR 0008 / 0009). The architect
decision here is: **add a new internal endpoint
`HEAD /internal/applications/{id}/owner/{userId}`** returning 204 (owned) or 404 (not
owned / not found), called from notification-service via a new `ApplicationOwnershipGateway`
adapter that wraps the existing `AppInternalRestClient`.

Why HEAD: cheapest possible call, no body required, mirrors the boolean question.

Alternative considered: reuse the existing `/internal/applications/upcoming-next-steps`
endpoint and filter; rejected, that endpoint is windowed and not ownership-shaped.

This is a **separate work item** for the application-service developer; the backend
ticket #157 should call it out as a dependency. Until it exists, the architect's
**fallback**: trust the `applicationId` in the request body and let the dispatch step
silently no-op for rows whose application no longer exists. The implementer should
prefer the proper check; the fallback is for sequencing only.

### 8. Persistence (Layer 3 out)

```
notification.custom_reminder (
    id              UUID        PK DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL,           -- references auth.user.id (no FK, cross-schema)
    application_id  UUID        NOT NULL,           -- references applications.application.id (no FK)
    title           VARCHAR(200) NOT NULL,
    note            TEXT,                            -- nullable
    trigger_at_utc  TIMESTAMPTZ NOT NULL,
    channels        TEXT        NOT NULL,           -- comma-joined: 'IN_APP' | 'EMAIL' | 'IN_APP,EMAIL'
    stage           VARCHAR(20),                     -- nullable: 'SCREENING' | 'INTERVIEW' | 'OFFER'
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    channels_fired  TEXT,                            -- populated when status becomes FIRED
    fired_at_utc    TIMESTAMPTZ,                     -- populated when status becomes FIRED
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_custom_reminder_status
        CHECK (status IN ('SCHEDULED', 'FIRED', 'CANCELLED')),
    CONSTRAINT chk_custom_reminder_stage
        CHECK (stage IS NULL OR stage IN ('SCREENING', 'INTERVIEW', 'OFFER')),
    CONSTRAINT chk_custom_reminder_channels_nonempty
        CHECK (length(trim(channels)) > 0)
);

CREATE INDEX idx_custom_reminder_user_status_trigger
    ON notification.custom_reminder (user_id, status, trigger_at_utc);
CREATE INDEX idx_custom_reminder_user_app
    ON notification.custom_reminder (user_id, application_id);
CREATE INDEX idx_custom_reminder_due
    ON notification.custom_reminder (status, trigger_at_utc)
    WHERE status = 'SCHEDULED';

CREATE OR REPLACE FUNCTION notification.trg_custom_reminder_updated() RETURNS TRIGGER
    LANGUAGE plpgsql AS $$ BEGIN NEW.updated_at := NOW(); RETURN NEW; END; $$;

CREATE TRIGGER trg_custom_reminder_before_update
    BEFORE UPDATE ON notification.custom_reminder
    FOR EACH ROW EXECUTE FUNCTION notification.trg_custom_reminder_updated();
```

Three indexes, each justified:
- `(user_id, status, trigger_at_utc)` for list-mine-upcoming and list-mine-all (the two
  common UI reads).
- `(user_id, application_id)` for list-by-application.
- Partial `(status, trigger_at_utc) WHERE status='SCHEDULED'` for the dispatcher poll;
  partial keeps it small even as FIRED rows accumulate.

`channels` as a comma-joined TEXT (not a Postgres array, not a child table) matches the
existing `interview_reminder_sent.channels` precedent (migration 043) and stays simple
for v1. The closed enum (2 values) makes the parse trivial.

The Panache entity / mapper / repository follow the existing notification-service shape:
`@Entity @Table(name="custom_reminder", schema="notification")`, explicit `@Column`
names, mapper translates `Set<CustomReminderChannel>` to/from the comma-joined string.

### 9. Migration number assignment

**Assigned: `db/init/046-notification-custom-reminders.sql`.**

Reasoning: the existing notification range is 040..045 (latest is `045-notification-
preferences-drop-redundant-idx.sql`). 046 is the next free number. The migration file
contains only the DDL above (table + indexes + trigger). Forward-only, no data
migration. As per CLAUDE.md, on an existing data volume the file must be applied by hand
(`podman exec -i jobhub-db psql ... < db/init/046-...sql`) and the notification-service
restarted.

The number has been posted as a comment on backend ticket #157.

### 10. API contract additions (already frozen in `notification-service.yaml`)

| Operation | Method/Path | Notes |
|---|---|---|
| `listMyCustomReminders` | GET `/notifications/custom-reminders?includeFired` | Default upcoming-only, asc by trigger. |
| `createCustomReminder` | POST `/notifications/custom-reminders` | 201 + `Location`; 400 (validation), 404 (app not owned). |
| `getCustomReminder` | GET `/notifications/custom-reminders/{id}` | 404 on non-owner. |
| `updateCustomReminder` | PUT `/notifications/custom-reminders/{id}` | Partial-update; 409 if not SCHEDULED. |
| `deleteCustomReminder` | DELETE `/notifications/custom-reminders/{id}` | Soft-cancel (sets CANCELLED); 204; idempotent; 409 if FIRED. |
| `listCustomRemindersByApplication` | GET `/applications/{applicationId}/custom-reminders?includeFired` | 404 on non-owner. |

Schemas added: `CustomReminderChannel`, `CustomReminderStage`, `CustomReminderStatus`,
`CustomReminderResponse`, `CreateCustomReminderRequest`, `UpdateCustomReminderRequest`,
`CustomReminderList`. `NotificationType` enum extended with `CUSTOM_REMINDER`. All new
items carry `x-implementation-status: planned`. `uniqueItems` was deliberately omitted
on the `channels` arrays (it triggers a Jackson `JsonDeserialize` import the generator
can't satisfy in api-contracts); uniqueness is enforced in the domain. The contract
compiles (`mvn -pl api-contracts -DskipTests compile` -> BUILD SUCCESS) so backend +
frontend can build in parallel against it.

### 11. Bug #153 root-cause and verdict

**Verdict: fold the fix into backend ticket #157.** Same code path, same PR, same
component-test class. Splitting would force a second PR that just adds a parameter to
the very interface #157 will already be extending.

**Root cause: five compounding holes on the preferences write/read path.** The first
three were identified pre-implementation by reading the source; backend developer #157
surfaced the remaining two during TDD and they are folded in here for posterity:

1. `NotificationPreferencesService.updatePreferences` (file
   `notification-service/src/main/java/com/davidcreate/jobhub/notification/domain/service/NotificationPreferencesService.java`)
   has signature
   `(UUID userId, Boolean weeklyDigestEmail, Boolean inAppNotificationsEnabled, Boolean
   interviewReminders, Boolean ghostedAlert)`. **`interviewReminderEmail` is missing
   from the parameter list.**
2. The merged builder in the same method never calls `.interviewReminderEmail(...)`, so
   Lombok's `@Builder` falls back to the primitive default `false` on every PUT. The
   `defaults()` helper has the same omission, so a first-time user also gets `false`.
3. `NotificationResource.updateNotificationPreferences` (file
   `notification-service/src/main/java/com/davidcreate/jobhub/notification/adapter/in/rest/NotificationResource.java`,
   lines 70..79) reads four getters off the contract DTO (`getWeeklyDigestEmail`,
   `getInAppNotificationsEnabled`, `getInterviewReminders`, `getGhostedAlert`) and
   forwards them, but **never reads `request.getInterviewReminderEmail()`**, so the
   value the UI sends is dropped at the REST adapter even before the service bug above
   would matter.
4. `NotificationPreferencesResponseMapper.toResponse(...)` (file
   `notification-service/src/main/java/com/davidcreate/jobhub/notification/adapter/in/rest/dto/NotificationPreferencesResponseMapper.java`)
   sets four properties on the contract response but **does not call
   `.interviewReminderEmail(...)`**. Even once holes 1..3 are closed, GET prefs would
   serialise the persisted value as the contract default rather than the actual state,
   so the UI would still render the toggle wrong on first load.
5. `UnrecognisedPreferencesFilter.RECOGNISED_FIELDS` (file
   `notification-service/src/main/java/com/davidcreate/jobhub/notification/adapter/in/rest/exception/UnrecognisedPreferencesFilter.java`)
   lists the four legacy fields but **omits `"interviewReminderEmail"`**. A PUT body
   containing only `{"interviewReminderEmail": true}` therefore looks "all-unknown"
   to the filter and gets rejected as 400 Bad Request, blocking the very write the
   bug fix needs to enable.

All five are in the same family ("the four-field code path forgot the fifth field
across read, write, and validation"). Splitting them across stories would have left a
trap door open in each surface.

End-to-end effect: the UI PUTs `interviewReminderEmail=true`, the REST adapter ignores
it, the service overwrites it to `false`, the entity persists `interview_reminder_email
= false`, and `InterviewReminderService.processItem` reads `prefs.isInterviewReminderEmail()
= false` → no email is ever sent regardless of what the toggle shows. There is no DB
bug, no Hibernate bug, and no scheduler bug; the value never reaches storage.

The fix backend ticket #157 must apply:

- Add `Boolean interviewReminderEmail` to `UpdatePreferencesUseCase.updatePreferences`
  (port interface) and to `NotificationPreferencesService.updatePreferences`
  (implementation), merge it into the builder, and add it to the `defaults()` helper.
- In `NotificationResource.updateNotificationPreferences`, forward
  `request.getInterviewReminderEmail()` as the new last argument.
- Add a regression component test: PUT with `interviewReminderEmail=true`, GET back,
  assert `true`; PUT with `interviewReminderEmail=false`, GET back, assert `false`; and
  a scheduler-level test that with `interviewReminders=true` and
  `interviewReminderEmail=true` the mailer is actually invoked. QAE will own the cases.
- PR closes #153 alongside #134.

No contract change is required for the fix: the OpenAPI spec already declares
`interviewReminderEmail` on both the response and the request schemas. This is a pure
service-side wiring fix.

### 12. Config keys (notification-service)

| Key | Default | Purpose |
|-----|---------|---------|
| `notification.custom-reminder.enabled` | `true` | Kill switch for the dispatch scheduler |
| `notification.custom-reminder.cron` | `0 * * * * ?` | One-minute tick (tolerance is the same minute) |
| `notification.custom-reminder.batch-size` | `200` | Max reminders processed per tick |
| `notification.custom-reminder.title.max-length` | `200` | Mirrored from contract; enforce in domain |
| `notification.custom-reminder.note.max-length` | `2000` | Mirrored from contract; enforce in domain |

Reuses the existing `notification.internal.service-key`,
`quarkus.rest-client.app-internal.url`, and `quarkus.rest-client.auth-internal.url` from
ADRs 0008/0009. No new env vars.

### 13. Test shape

| Layer | What | How |
|-------|------|-----|
| Domain: invariant tests on `CustomReminder` create/edit | future-only, channels non-empty, title bounds | Plain JUnit |
| Unit: `CustomReminderService` use-case handlers | owner-scoping, status guards, channel normalisation, gating decision call into prefs | Mockito, mock all outbound ports |
| Unit: `CustomReminderResponseMapper` / `CustomReminderListMapper` | round-trip | Plain JUnit |
| Unit: persistence mapper | `Set<Channel>` to/from comma-joined string | Plain JUnit |
| Component: `CustomReminderResource` happy + 4xx + 409 paths | full CRUD + list-mine + list-by-application against DevServices DB | `@QuarkusTest`, fixed seed |
| Component: `CustomReminderDispatchScheduler` | due reminder triggers in-app + email, gating drops email when master is off, FIRED row, idempotent | `@QuarkusTest` + WireMock for app/auth-service, mock mailer port |
| **Regression for #153**: `NotificationResource` PUT preferences | round-trip `interviewReminderEmail` true/false | Component test class extended |
| **Regression for #153**: `InterviewReminderService` | with `interviewReminders=true` and `interviewReminderEmail=true`, mailer is invoked | Unit, Mockito |

## Consequences

- **Positive:** Reuses every existing notification-service pattern (scheduler shape,
  Qute mailer, Panache repo, internal-call pattern, status-as-log idempotency). No new
  cross-cutting infra, no new Maven module, no new env vars.
- **Positive:** The fold-in of #153 puts the fix on exactly the same code path the
  story extends, so the developer touches the file twice (once for the new param, once
  for the new use cases) instead of twice in two PRs. QAE writes both regression cases
  in the same component test class.
- **Positive:** The gating rule is the same rule every other channel in the service
  follows; "master toggle wins" is a single contract for the user to learn.
- **Negative / cost:** A new internal endpoint in application-service
  (`HEAD /internal/applications/{id}/owner/{userId}`) is needed for clean ownership
  checks. This is a small but real cross-service dependency for #157. The implementer
  may temporarily fall back to "trust the request body" if sequencing requires it; the
  ADR records the fallback so it isn't silent.
- **Negative / accepted:** "Email me" is gated by `interviewReminderEmail` until a
  later story splits out a dedicated `customReminderEmail` flag. ADR 0010 already
  binds the UI label "Also email me for alerts" to this flag, so the user's mental
  model is consistent.
- **Negative / accepted:** `uniqueItems` is not in the contract because the generator
  can't compile it inside api-contracts. The domain de-dups; backend must add a service-
  side test for "POST with `[EMAIL, EMAIL]` returns one EMAIL in the response".
- **Follow-ups:**
  - Backend ticket #157: implement the design + fold #153 fix; closes both issues.
  - application-service developer (separate ticket if not folded into #157): the
    `HEAD /internal/applications/{id}/owner/{userId}` endpoint + `ServiceKeyFilter`
    coverage.
  - PDA / QAE: refine acceptance criteria and write test cases against this ADR + the
    frozen contract slice.
  - Frontend: implement against the frozen contract; the "channel gated by preference"
    fall-through is invisible at the UI level (the UI sends the request, the dispatcher
    silently drops gated channels), so no UI copy is owed beyond what story #135
    already provides.
  - Future ADR: if product later wants per-reminder gate-override or a dedicated
    `customReminderEmail` master flag, open a new ADR (contract + service + numbered
    migration in the notification range).

## Alternatives considered

- **Per-reminder override of master gates** (a `bypassChannelGates: boolean` on the
  request). Rejected for v1: contradicts the master-toggle contract every other
  notification follows, surprises users, and the user can already achieve the effect
  by flipping the master toggle on.
- **Separate `custom_reminder_fired` audit log table** mirroring `interview_reminder_sent`.
  Rejected: a custom reminder is one-shot, so the row's own status is the log. The
  H24/H1 multi-offset case that justified the separate table for interview reminders
  does not apply here.
- **A `recurring` flag on `CustomReminder` for v1.** Rejected: out of scope per story
  #134's "Recurring reminders (one-shot only for v1)". Adding a column now would
  invite half-built recurrence logic; defer until a story actually asks for it.
- **Postgres `text[]` for `channels` (or a child `custom_reminder_channel` table).**
  Rejected: heavier than warranted for a closed two-value enum. Matches the existing
  `interview_reminder_sent.channels` precedent (comma-joined TEXT).
- **Putting the new endpoints inside `NotificationResource`.** Rejected: the resource
  is already two stories deep (preferences + notification center). A sibling
  `CustomReminderResource` keeps the file size honest and matches the way the
  schedulers are split.
- **Splitting #153 into its own ticket.** Rejected: same file, same code path,
  same component test class as #157. Folding eliminates a coordinating PR with no
  downside; the architect ticket here records the root cause so the developer doesn't
  have to rediscover it.
- **Adding `jackson-databind` as a `provided` dependency on api-contracts to allow
  `uniqueItems: true`.** Rejected: introduces a transitive shape across every consuming
  service for a property the domain can enforce in one line. Dropping `uniqueItems` is
  the smaller change.

## 14. Addendum: PDA / QAE follow-up answers (story #134)

Posted 2026-06-20 after PDA (#155) shipped `docs/specs/US6-custom-reminders.md` and QAE
(#156) shipped 162 test cases. These answers unblock the developer tickets (#157 backend,
#158 frontend, #160 application-service) and lock the open PDA-TAG items so QAE can
finalise assertions. Numbering tracks the questions surfaced on #134.

### A1. PDA Q1: ownership check for `listCustomRemindersByApplication` before #160 lands

**Decision: option (b), with empty list on non-owner.** The listing endpoint
`GET /applications/{applicationId}/custom-reminders` filters on
`WHERE user_id = jwt.userId AND application_id = :applicationId` against
`notification.custom_reminder`. Because create is the only path that inserts rows and
already verifies ownership, no row owned by user A can ever exist under user B's
`applicationId`. A query by user B for user A's application therefore returns an empty
list naturally, with no cross-owner leak possible at the table level. Listing is **not**
blocked by #160.

Why empty list (not 404): the resource cannot distinguish "valid application that isn't
yours" from "valid application that's yours but has zero reminders" without an
ownership call. Returning 404 in the former case would leak the application's existence
to a non-owner (the contract explicitly avoids this leak elsewhere), and returning 404
when the user simply has no reminders yet would be wrong. Empty list is correct for both
shapes and matches REST list semantics.

Once #160 ships, the create path tightens (the existing `ApplicationOwnershipGateway`
call replaces the ADR-7 fallback) but the listing endpoint keeps the owner-scoped query
unchanged: there is no value in adding a HEAD round-trip for a query that is already
owner-safe. The contract line "Returns 404 if the application is not owned" in the
listing operation is therefore relaxed in the addendum: list returns 200 + empty array
for non-owner. QAE: update AC-LS-5 / CR-C cases to assert 200 + `items: []` instead of
404 on the by-application listing for a non-owner application.

### A2. PDA Q3: email body fallback when `note` is null

**Decision: title only on a single line; no parenthetical filler.** The Qute template
renders `{reminder.note}` when present and falls back to the title alone when null or
blank (after trim). No "(no extra details)" or similar copy: the subject already carries
the title, and a body that just restates it without explanatory parenthetical is the
cleanest signal that the user did not attach a note. The body still carries the
existing notification-email chrome (header, footer, "view application" CTA from BR-13)
so the email is never empty.

QAE assertion: when the seed reminder has `note = null`, the rendered email body
contains the title exactly once as a paragraph and does not contain any literal
"(no extra details)" or "No note" string.

### A3. QAE CR-U-007: exception class for blank or too-long `title`

**Decision: introduce a new domain exception `CustomReminderInvalidTitleException`,
maps to 400.** Reusing `CustomReminderInvalidChannelsException` would be wrong (channels
vs title are independent invariants and the exception name would mislead future
readers), and there is no existing generic "invalid input" exception in
notification-service. Add `CustomReminderInvalidTitleException extends RuntimeException`
under `domain/exception/`, with a matching `@Provider
CustomReminderInvalidTitleExceptionMapper` returning 400 with
`{"error":"Invalid Title","message":"<detail>"}`. Thrown by the domain model factory /
use-case handler when `title` is blank after trim or exceeds 200 characters (config key
`notification.custom-reminder.title.max-length`, already in ADR section 12). The same
exception covers both create and update paths.

### A4. QAE CR-U-041: second `DELETE` on an already-CANCELLED reminder

**Decision: pure no-op at the service layer.** `CancelCustomReminderUseCase.cancel(...)`
loads by `(id, userId)`. If `status == CANCELLED` it returns without calling
`markCancelled` and without writing the row. If `status == SCHEDULED` it calls
`markCancelled` and writes. If `status == FIRED` it throws
`CustomReminderNotScheduledException` (409). The resource always returns 204 on a
non-thrown path, so idempotency at the contract is preserved while the DB sees no
redundant UPDATE, no `updated_at` churn, and no trigger fire. QAE: assert in
`CR-U-041` that `repo.markCancelled` is verified `never()` for the CANCELLED-already
case, and that the resource still returns 204.

### A5. QAE CR-C-033 / CR-UI-034: UI behaviour when `DELETE` returns 404 (stale-list race)

**Decision: silent refetch + remove row, no toast.** The 404 indicates another tab or
the dispatcher already finalised the reminder; the user's intent ("get rid of it") is
satisfied either way. Showing a one-line notice would imply user error where there is
none. The UI handler swallows the 404, removes the row from local state immediately,
and triggers the normal list refetch in the background so any other server-side state
(e.g. a sibling FIRED in the same race) appears correctly. All other 4xx / 5xx delete
errors still surface an inline error as today. QAE: `CR-UI-034` asserts no toast / no
visible error text, row removed from rendered list, and that the listing fetcher is
called once after the 404.

### A6. QAE CR-UI-060: display format for `triggerAtUtc`

**Decision: absolute date + time in the user's locale and timezone, no relative
phrasing.** Format token: `Intl.DateTimeFormat(userLocale, { weekday: "short", day:
"numeric", month: "short", hour: "2-digit", minute: "2-digit" })`, producing strings of
the shape "Mon 22 Jun, 14:30" (locale-dependent). Relative phrasings ("in 3 hours") are
brittle around DST and don't read well for reminders weeks out (which is the common
case). The notification bell already uses a relative `timeAgo()` for delivered
notifications (`NotificationBell.jsx` line 32), but that is a different signal (when
something *happened*) versus this one (when something *will happen*): mixing the two
in the same product would confuse users. QAE `CR-UI-060`: assert the rendered string
contains both date and time components and does NOT contain "ago" / "in " / "minutes".

### A7. QAE CR-UI-070: icon + label for `CUSTOM_REMINDER` in the bell dropdown

**Decision: icon `clock`, no per-type label.** Extend `TYPE_ICON` in
`JobHub-ui/src/components/NotificationBell.jsx` with
`CUSTOM_REMINDER: "clock"`. The `clock` glyph already exists in `Icon.jsx` (line 45),
so no new asset, no new dependency. It distinguishes custom reminders ("user-set time")
from `INTERVIEW_REMINDER` which uses `calendar` ("scheduled event") and from
`GHOSTED_ALERT` which uses `alert-circle`. No textual type label is added: the bell
dropdown already shows the notification `title` + `body`, and adding a type chip would
be inconsistent with the existing rows. QAE `CR-UI-070`: assert
`data-testid="notification-icon-CUSTOM_REMINDER"` exists, its child SVG matches the
`clock` glyph, and the row renders without falling back to the default `info` icon.
