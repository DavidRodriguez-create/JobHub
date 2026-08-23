# ADR 0031: Notification category taxonomy derived from notification type

- **Status:** Accepted
- **Date:** 2026-08-08
- **Deciders:** jobhub-architect, David R H
- **Affects:** api-contracts (`notification-service.yaml`), notification-service (domain + REST DTO), JobHub-ui (notification row presentation)
- **Story / tickets:** story #439 (notification rework), architect ticket #531, backend ticket #534

## Context

The all-notifications page renders `NotificationIdentity` (`JobHub-ui/src/components/NotificationIdentity.jsx`)
on **every** notification row, unconditionally, from `screens/Notifications.jsx`. That component
calls `resolveCardIdentity()` in `components/notificationPresentation.js`, whose `resolved` gate is
`Boolean(jobTitle)` (BR-244-1). When `jobTitle` is null it renders the fixed
`FALLBACK_LABEL = "Application no longer available"`.

An account-level notification never has an application, so it never has a `jobTitle`. A
`SECURITY_RECOMMENDATION` row therefore renders "Application no longer available", which is not a
degraded state at all: there was never an application to lose. The same defect applies to `SYSTEM`.

The root cause is that the UI has no way to ask "is this notification even about an application?".
It only has `NotificationType`, so the only available fix in the UI alone is to hard-code a list of
account-ish type strings in the frontend. That list would silently rot the moment a new type is
minted server-side, and it duplicates a taxonomy that belongs to the notification domain.

Constraints in scope:

- notification-service is **hexagonal**: `domain/{model,port,service}` carries zero framework
  annotations, adapters translate at the boundary.
- **Contract-first**: `api-contracts/src/main/resources/openapi/notification-service.yaml` is the
  single source of truth for the wire shape.
- The DB schema is owned by `db/init/*.sql`, and Hibernate is `validate` in prod.
- Locked scope for story #439: category exists as a concept in the contract and the UI, `JOB_POST`
  is **taxonomy only** (no new notification types are minted), and there are **no per-category tabs
  or filters** on the notifications page.

## Decision

We will introduce a `NotificationCategory` concept with three values, **derived server-side** from
`NotificationType`, and expose it as a **required, non-null** property on `NotificationResponse`.

### 1. The enum

```
APPLICATION | JOB_POST | ACCOUNT
```

- `APPLICATION`: the notification is about one of the user's job applications. `applicationId` is
  expected to be non-null and a client may render application-scoped context (company, job title,
  logo, deep link).
- `JOB_POST`: the notification is about a job post rather than an application. Reserved. No current
  `NotificationType` maps to it.
- `ACCOUNT`: the notification is about the user's account or the platform itself. It has no
  application and no job-post subject, `applicationId` is always null, and a client must render no
  subject-scoped context.

### 2. Derived, not persisted

The category is a **pure function of `NotificationType`**, computed server-side on every read. There
is **no** `category` column, **no** backfill, **no** check constraint and **no** migration.

In hexagonal terms the taxonomy is domain knowledge, so it lives in the domain:

- `domain/model/NotificationCategory` (plain Java enum, no annotations).
- The type-to-category mapping is a pure method on `domain/model/NotificationType`
  (for example `NotificationType#category()`), implemented as a **Java 21 exhaustive switch
  expression over the enum with no `default` branch**.
- `Notification` (the domain model) does **not** gain a `category` field. Category is derived state,
  not stored state, and adding it would create a second in-memory copy that a builder call could
  contradict.
- `adapter/in/rest/dto/NotificationResponseDto#from` translates the domain category into the
  generated `contract.model.NotificationCategory`, exactly as it already does for `type`. The
  adapter translates, it does not decide.

### 3. Type to category mapping

| NotificationType | Category | Grounding in current code |
|---|---|---|
| `INTERVIEW_REMINDER` | `APPLICATION` | `InterviewReminderService` builds the notification with `.applicationId(applicationId)` taken from the upcoming-next-step item. |
| `GHOSTED_ALERT` | `APPLICATION` | `GhostedAlertService` builds with `.applicationId(app.getId())`. |
| `APPLICATION_UPDATE` | `APPLICATION` | Declared in `NotificationType` but **never minted** by any current service (only test seeds use it). Placed by intent: it is by definition an update to an application. |
| `CUSTOM_REMINDER` | `APPLICATION` | Always application-scoped, verified end to end: `CreateCustomReminderRequest.applicationId` and `CustomReminderResponse.applicationId` are both `required` in the contract; `CustomReminderService.create()` calls `ownershipGateway.isOwnedByUser(applicationId, userId)` and throws `ApplicationNotOwnedException` before building; `CustomReminderDispatchService` copies `reminder.getApplicationId()` onto the notification. There is **no** standalone (application-less) custom reminder path. |
| `SECURITY_RECOMMENDATION` | `ACCOUNT` | `SecurityRecommendationService` builds the notification with no `applicationId` at all. It is raised per user from `TwoFactorStatusGateway.fetchUsersWithoutTwoFactor()`. |
| `SYSTEM` | `ACCOUNT` | Never minted by a service today; seeds and component tests treat it as the canonical null-`applicationId` row (`NS-C-03`, `TC-B-C-24`, `NS244-C-04`). |

No type maps to `JOB_POST` in story #439. The value is contract taxonomy only.

### 4. Unknown / future-type fallback

Two distinct rules, at two distinct boundaries.

**Server side (compile-time gate).** The mapping is an exhaustive `switch` expression with **no
`default` branch**. Adding a constant to `NotificationType` then fails compilation until the author
explicitly assigns its category. We deliberately do **not** provide a silent server-side default: a
wrong-by-omission category is exactly the class of bug this ADR exists to kill. If a runtime path
ever produces an unmapped value despite this (for example a value read back from a `VARCHAR(50)`
column written by an older or hand-edited row), it resolves to `ACCOUNT`, the safe value.

**Client side (runtime rule).** A client that receives a category string it does not recognise, or a
`NotificationResponse` in which `category` is absent (an older server during a rolling deploy), MUST
fall back to **`ACCOUNT`** semantics. Falling back to `APPLICATION` is forbidden.

**The UI rule that fixes the defect.** `screens/Notifications.jsx` renders the application identity
row **only** when the effective category is `APPLICATION`:

- `APPLICATION`: render `NotificationIdentity` as today, including the existing `FALLBACK_LABEL`
  behaviour, which now only fires for a genuinely unresolvable application.
- `JOB_POST`: a distinct branch exists but renders no application identity row in this story.
- `ACCOUNT`, unknown value, `null`, or field absent: render **no** identity row at all.

The invariant, stated once: **the UI never renders the application identity row speculatively.** It
renders it only on a positive `APPLICATION` signal, never on the absence of a signal.

### 5. Required vs nullable on `NotificationResponse`

`category` is **`required` and non-nullable**. It is derived from `type`, which is itself required,
so the server can always compute it: there is no state in which a notification has a type but no
category. Making it nullable would push a permanent "maybe absent" branch into every consumer to
model a transient rollout window that the client-side unknown-value rule already covers.

Both the new schema and the new property carry `x-implementation-status: planned` until backend
ticket #534 ships the derivation, per the api-contracts convention.

### 6. Database migration range for ticket #534

**N/A. No `db/init` migration is assigned to #534.** This change adds no column, no constraint and
no index. `db/init/041-notification-notifications.sql` is untouched. (For the record, the next free
number in `db/init/` is `059`; it is not reserved by this story.)

## Consequences

- Positive: the account-notification defect is fixed by a positive signal from the server rather
  than by a frontend heuristic. The UI stops guessing from `jobTitle` nullability.
- Positive: zero migration, zero backfill, zero drift risk. Category cannot disagree with type
  because it is computed from it.
- Positive: the exhaustive switch makes every future notification type a compile-time decision about
  its category, so the taxonomy cannot rot by omission.
- Positive: `JOB_POST` lands as taxonomy now, so the future job-post notification story is additive
  on the client (the branch already exists) instead of another breaking presentation change.
- Negative / cost: the category cannot vary per notification instance. If we ever need two
  notifications of the same type to sit in different categories, this ADR must be superseded and a
  persisted column introduced with a backfill migration. We judge that unlikely: category is a
  property of the type's meaning, not of an individual event.
- Negative / cost: category cannot be used as a SQL filter predicate. Filtering by category means
  `WHERE type IN (...)` expanded from the mapping, not `WHERE category = ?`. Acceptable, because
  story #439 explicitly excludes per-category tabs and filtering. If filtering ever lands with a
  measured performance need, the expansion is still index-friendly against the existing `type`
  column.
- Follow-ups:
  - Backend ticket #534: `domain/model/NotificationCategory`, the exhaustive mapping on
    `NotificationType`, and the `NotificationResponseDto` translation. Unit tests: one case per type
    asserting its category, plus a test asserting every `NotificationType.values()` entry resolves
    to a non-null category (so a new type without a mapping fails the suite as well as the compiler).
    Component test: assert `category` is present on `GET /notifications` rows for both an
    application-scoped and an account-scoped seed row.
  - Frontend ticket: the category branch in `screens/Notifications.jsx` and a
    `categoryOf(notification)` helper in `components/notificationPresentation.js` implementing the
    unknown/missing to `ACCOUNT` rule. `NotificationIdentity.jsx` itself needs no change: the fix is
    that it stops being called for non-application rows.
  - When the property is implemented, flip `x-implementation-status` from `planned` to `existing` on
    `NotificationCategory` and on `NotificationResponse.category`.

## Alternatives considered

- **Persist a `category` column on `notification.notifications`** (rejected): requires a migration, a
  backfill of every existing row, and a check constraint, and it creates a second source of truth
  that can drift from `type` on any write path that forgets it. It buys only per-instance variance
  and SQL filterability, neither of which story #439 needs.
- **Derive the category in the UI from a hard-coded type list** (rejected): the fastest fix, but it
  duplicates domain taxonomy in the frontend and silently mis-renders the next new type. It is the
  same class of coupling that produced this defect.
- **Suppress the identity row when `applicationId` is null** (rejected): it fixes the visible symptom
  without naming the concept, it conflates "has no application" with "application unresolvable", and
  it would wrongly suppress the legitimate `FALLBACK_LABEL` for a genuinely deleted application
  whose `applicationId` is still present. It also gives the future `JOB_POST` case nothing to hang
  off.
- **Reuse `NotificationType` and rename nothing** (rejected): the contract already misused the word
  "category" in `NotificationType`'s description. Story #439 needs a genuinely coarser axis, so the
  two concepts are separated and `NotificationType`'s description is reworded to say "kind".
- **Two values only, `APPLICATION` and `ACCOUNT`** (rejected): it fixes today's bug but forces a
  breaking taxonomy change the moment job-post notifications arrive. Adding `JOB_POST` now costs one
  enum constant and one inert UI branch.
- **Nullable `category`** (rejected): see section 5. The transient rollout window is a client
  concern, already covered by the unknown-value rule, and does not justify a permanently optional
  field.
