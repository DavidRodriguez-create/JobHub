# ADR 0013: Resolve the per-application reminders routing collision by changing the contract path (approach B)

- **Status:** Accepted (supersedes the earlier proxy-rule decision recorded in this ADR)
- **Date:** 2026-06-26
- **Deciders:** jobhub-architect + human reviewer (story #175 / sub-issue #199)
- **Affects:** api-contracts (notification-service.yaml, CHANGED), notification-service (CustomReminderResource @Path + generated interface), JobHub-ui (src/api/custom-reminders.js + its unit test)

## Context

notification-service owns custom reminders. The per-application list endpoint was defined at
`GET /applications/{applicationId}/custom-reminders` (port 8084, `CustomReminderResource`,
contract `notification-service.yaml`, ADR 0011). It is owner-scoped (returns 200 + empty list
for a non-owned/absent application, ADR 0011 addendum A1).

Both UI reverse proxies (`JobHub-ui/vite.config.js` dev, `JobHub-ui/nginx.conf` prod) route the
entire `/applications` prefix to application-service (8083), which has no such route. So the
per-application reminders list was always misrouted and errored, and the post-create refetch was
misrouted so a newly created reminder never appeared. Create itself works because it posts to
`/notifications/custom-reminders` (8084), which proxies correctly.

The root cause is that two services share the `/applications` URL prefix while the proxies only
encode the prefix, not the more-specific sub-path.

## Decision

We resolve the collision at the contract level (approach B): the per-application listing no longer
lives under `/applications`. It is folded into the existing `/notifications/custom-reminders`
collection as an optional `applicationId` query-param filter, so every reminders path sits cleanly
under the `/notifications` prefix that already proxies to notification-service (8084). No proxy
regex is added.

**New frozen path:**

```
GET /notifications/custom-reminders?applicationId={uuid}&includeFired={bool}
```

- `applicationId` (query, optional, uuid): when supplied, scope the result to that application
  (owner-scoped: `WHERE user_id = jwt.userId AND application_id = :applicationId`, 200 + empty
  list for a non-owned/absent application, ADR 0011 addendum A1). When omitted, return all of the
  user's reminders, exactly as before.
- `includeFired` semantics are unchanged.
- The response model is unchanged (`CustomReminderList`).

This is the smallest possible contract delta: the dedicated `listCustomRemindersByApplication`
operation and the whole `/applications/{applicationId}/custom-reminders` path are removed, and the
existing `listMyCustomReminders` operation gains one optional query parameter. The shape is also the
cleanest REST choice: filtering a collection the service already owns, rather than introducing a new
nested sub-resource path.

### Why a query-param filter, not a nested `/notifications/applications/{id}/custom-reminders`

`GET` and `POST /notifications/custom-reminders` (list-mine, create) already exist as the canonical
reminders collection. "Reminders for one application" is a filtered view of that same collection, so
a query parameter is the idiomatic REST expression and adds zero new path surface. A nested path
under `/notifications` would work too but invents a second way to address the same resource and a
second generated handler method for no benefit.

### Generated-interface consequence (notification-service)

The OpenAPI jaxrs-spec generator (`useTags=true`) groups operations into interfaces by the path
root segment, not by the `tags` field (verified: `/notifications/*` -> `NotificationsApi`,
`/applications/*` -> `ApplicationsApi`). Removing the `/applications` path means **`ApplicationsApi`
is no longer generated at all**; the listing method moves onto `NotificationsApi` (alongside
`listMyCustomReminders`, which it now *is*). So `CustomReminderResource` must switch from
`implements ApplicationsApi` to `implements NotificationsApi` and update its `@Path` and method
signature accordingly (see Consequences for the consumer list).

## Consequences

- Positive: the collision is gone at the source. No shared-`/applications`-prefix coupling, no proxy
  regex to maintain, no precedence-ordering gotchas in nginx/Vite. Every reminders call now flows
  through the single `/notifications` proxy rule that already exists.
- Cost: the contract is no longer "frozen unchanged" for #175. Three consumers must change
  (backend resource + generated-interface switch; UI client; UI client unit test). No DB schema
  change, no migration: the persistence query is the same owner-scoped + application_id filter, now
  driven by a query param instead of a path param.
- The proxy files (`vite.config.js`, `nginx.conf`) need **no** new rule. The pre-existing
  `/notifications` -> 8084 entry already covers the new path. (If a `/applications` regex rule was
  added in an earlier attempt, it should be removed as dead config.)

## Consumers to change (ticket scope)

Backend (notification-service):
- `notification-service/.../adapter/in/rest/CustomReminderResource.java`: change
  `@Path("/applications/{applicationId}/custom-reminders")` to `@Path("/notifications")` (or fold the
  method into the existing notifications resource), switch `implements ApplicationsApi` to
  `implements NotificationsApi`, and change the handler signature from
  `listCustomRemindersByApplication(UUID applicationId, Boolean includeFired)` to the generated
  `listMyCustomReminders(UUID applicationId, Boolean includeFired)`, mapping a null `applicationId`
  to the list-all path and a non-null `applicationId` to the owner-scoped filter. The
  `ListCustomRemindersByApplicationUseCase` (domain port) and its persistence query are reusable: the
  query is unchanged, only the entry point and parameter source change.
- Any component test asserting `GET /applications/{id}/custom-reminders` must target
  `GET /notifications/custom-reminders?applicationId={id}` and the merged list-all/filtered behaviour.

Frontend (JobHub-ui):
- `src/api/custom-reminders.js`: `listCustomRemindersByApplication` builds the path from
  `/notifications/custom-reminders` with `applicationId` (and optional `includeFired`) query params
  instead of `/applications/${applicationId}/custom-reminders`. Update the header path comment too.
- `src/test/unit/custom-reminders.api.test.js` (CR-UI-007): assert the new query-param path.

## Empty / error UX contract (unchanged)

The per-application reminders view keeps three distinct states:
- **loading**: while the request is in flight.
- **empty**: a 200 response with `content.length === 0` renders an empty state with an
  "add one now" affordance (not an error).
- **load-error**: a non-2xx / network failure renders a retryable error state, distinct from empty.

A correctly-routed empty list (200 + 0) must read as "no reminders yet", never as a failure.

## Alternatives considered

- **(A) Fix it purely in the two proxies** with a more-specific regex location/key that routes
  `/applications/{id}/custom-reminders` to 8084 ahead of the `/applications` prefix. This was the
  earlier recommendation. Rejected by the human reviewer in favour of B: it leaves a documented
  shared-prefix coupling that every future notification-owned per-application sub-resource must
  re-encode in both proxies, and it encodes service-routing knowledge in proxy regexes rather than in
  the contract. B removes the collision instead of routing around it.
- **(C) Nested `/notifications/applications/{id}/custom-reminders`**: keeps a path param but adds a
  second way to address the same collection and a second generated method. Rejected in favour of the
  query-param filter, which is the smaller and more idiomatic delta (see rationale above).
- **A gateway/BFF that fans out the `/applications` prefix**: over-engineering for a two-rule
  collision; JobHub has no gateway tier and this story does not justify one.
