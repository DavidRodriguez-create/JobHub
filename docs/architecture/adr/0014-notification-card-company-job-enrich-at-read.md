# ADR 0014: Notification cards carry company + job title via enrich-at-read

- **Status:** Accepted
- **Date:** 2026-06-26
- **Deciders:** jobhub-architect (story #207, ticket #212)
- **Affects:** notification-service, api-contracts (notification-service.yaml), application-service (new internal endpoint), JobHub-ui

## Context

Story #207 (req 4) requires each notification card to show the tied application's company
icon (the initial-based `CoLogo` chip) and the job post / application name as the card's
primary identifier. Today the `Notification` domain model and `NotificationResponse` carry
only `applicationId`: no company name, no job title. The payload must start carrying that
data.

Two sourcing strategies are on the table:

- **Enrich-at-read:** resolve company + job title at response time, by calling
  application-service through notification-service's existing internal client/gateway layer.
- **Denormalise-at-write:** persist `company` / `job_title` columns on the notification row,
  filled by each notification writer, via a new `db/init` migration.

Relevant JobHub conventions in scope: notification-service is Hexagonal (CLAUDE.md decision
guide); api-contracts OpenAPI is the single source of truth (contract-first); the database is
owned by `db/init/*.sql` (schema-per-service, `notification` schema, migration band
`040 to 049`).

Two facts from surveying the code shape the decision:

1. notification-service **already owns an internal client + gateway layer** to
   application-service (`adapter/out/client/application/`, `AppInternalRestClient`,
   `ApplicationOwnershipGatewayAdapter`, plus interest-profile / stale / upcoming gateways),
   guarded by `X-Service-Key`. Adding one more read path is incremental, not new
   infrastructure.
2. The notification **writers do not uniformly have the data at write time.** Ghosted-alert
   writers hold `company` + `jobTitle` (from `StaleApplicationResponse`); interview-reminder
   writers hold `companyName` + a next-step label (not the job title proper); custom-reminder
   dispatch holds only `applicationId` and would need a fresh resolve at write anyway. So
   denormalise does not avoid the upstream call, it just moves it and then freezes a copy.

The list endpoint (`GET /notifications`) is paginated up to 100 items, so a naive per-row
fan-out to application-service is the main risk to weigh.

## Decision

We will **enrich-at-read**. notification-service resolves the tied application's company name
and job title at response-build time and populates the two new nullable
`NotificationResponse` fields. We will:

- Keep notification-service **Hexagonal**. Add one outbound port
  `domain/port/out/ApplicationSummaryGateway` with a method that resolves a batch of
  application ids to `(applicationId -> company, jobTitle)`, implemented by a new adapter
  under `adapter/out/client/application/` reusing the existing `X-Service-Key` config and a
  client method on (or alongside) `AppInternalRestClient`.
- Add a **new internal endpoint on application-service**:
  `GET /internal/applications/summaries?ids=...` (service-key guarded) returning a compact
  list of `{ applicationId, company, jobTitle }`, so the resolve is a **single batched call
  per page**, not one call per notification (no N+1).
- Resolve in the read use case after fetching the notification page: collect the distinct
  non-null `applicationId`s, make one gateway call, and map results onto each
  `NotificationResponse`. Field names are frozen as `company` and `jobTitle` (nullable),
  matching application-service's existing `company` / `jobTitle` naming.
- Make the resolve **best-effort**: on a missing, unowned, or upstream-unavailable
  application, leave `company` / `jobTitle` null and let the UI fall back to a generic
  icon + label. A resolve failure must never fail the notification list response.
- Make the **custom-reminder title non-editable** (req 4): `UpdateCustomReminderRequest`
  drops `title`; the create-time title is preserved; only `note`, `triggerAtUtc`, `channels`,
  `stage` are editable. This is a contract change in the same freeze, no schema change.

No `db/init` migration is required for this story on either service: **migration is N/A**.

### Frozen application-service contract slice (`application-service.yaml`)

This decision adds one new internal operation to application-service. It is frozen here so
notification-service (consumer), application-service (producer), and the frontend all build
against the same shape. Two backend developers depend on these names being final.

- **Operation:** `getApplicationSummaries`, `GET /internal/applications/summaries`, tag
  `Internal`, `x-implementation-status: planned`, guarded by the existing `serviceKeyAuth`
  (`X-Service-Key` header), consistent with the other `/internal` operations.
- **Query parameter:** a single `ids` parameter, OpenAPI `style: form, explode: false`, that
  is a comma-joined list (`ids=<uuid>,<uuid>,...`), `minItems: 1`, `maxItems: 100`. The 100
  cap matches the maximum notification page size, so one page resolves in one call. Comma-join
  (not repeated `ids=`) was chosen to keep one short query string for a page of ids and to
  match a single batched call shape.
- **Response model (200):** `ApplicationSummaryListResponse` wrapping
  `items: ApplicationSummaryResponse[]`, where `ApplicationSummaryResponse` is
  `{ applicationId (uuid), company (string), jobTitle (string) }`, all required. The
  `company` / `jobTitle` names match application-service's existing `StaleApplicationResponse`
  convention, so the two notification fields line up with their source.
- **Unknown-id behaviour:** ids that do not resolve (not found, owned by another user, or
  otherwise unresolvable) are **omitted** from `items`, not returned as null entries. The
  response may therefore be shorter than the request, in any order, or empty. Callers map by
  `applicationId` and treat any absent id as unresolved (the card degrades to a generic
  icon + label). Omit-not-null keeps every returned item fully populated and means the
  notification-service mapper does not have to special-case null fields inside an item.
- **Status codes:** `200` for any partial or empty resolve (a partial result is success, not
  an error); `400` on missing `ids`, a malformed UUID, an empty list, or more than 100 ids;
  `401` on a missing or invalid `X-Service-Key` (the standard `/internal` guard); `500` on an
  unexpected server error. No `404`: a fully-unresolved batch is still a `200` with empty
  `items`, since per-id resolution is best-effort by design.
- **Read-only, no schema change:** the endpoint is a read-only aggregation over the existing
  `applications` schema. `company` and `jobTitle` come from the same columns the stale and
  upcoming-next-steps internal endpoints already read (the crawled-job snapshot's
  `title`/`company` or the manual-entry job's `title`/`company`). No new columns, no DDL, so
  **no `db/init` migration on application-service** (the next free application number remains
  unused by this story).

## Consequences

- Positive: notification rows stay a pure event log, with no denormalised copies to keep
  consistent. If a user edits an application's company or job title, every existing
  notification card reflects the new value automatically (consistency is free).
- Positive: no schema migration, no backfill of historical rows, no new write-path coupling
  in five notification writers.
- Positive: reuses the existing internal client/gateway + `X-Service-Key` pattern already
  proven in notification-service; the layering stays clean (new port in `domain/port/out/`,
  new adapter in `adapter/out/client/application/`).
- Negative / cost: the notification list now has a runtime dependency on application-service.
  Mitigated by (a) a single batched call per page and (b) best-effort degradation so the list
  still renders if application-service is slow or down.
- Negative / cost: small added read latency per page (one extra internal round trip).
  Acceptable for an interactive list; can be cached later if it ever matters.
- Follow-ups:
  - Backend (#215): new `GET /internal/applications/summaries` on application-service (its
    own contract slice + handler), new `ApplicationSummaryGateway` port + adapter +
    client method in notification-service, populate `company` / `jobTitle` in
    `NotificationResponseDto.from(...)`, drop title handling on the custom-reminder update
    use case. WireMock for the new client (notification-service already uses WireMock).
  - Frontend (#216): render `CoLogo` from `company`, show `jobTitle` as the primary
    identifier, graceful fallback when both are null, drop the title field from the
    custom-reminder edit form (body-only).
  - QAE: component test for the batched resolve, the null/degraded path, and the
    body-only reminder update.

## Alternatives considered

- **Denormalise-at-write (persist `company` / `job_title` on the notification row, new
  `04N` migration)**: rejected. It does not remove the upstream call (custom-reminder and
  interview writers still must resolve at write), it adds a schema migration plus a backfill
  for existing rows, it freezes a stale copy that diverges when the application is later
  edited, and it spreads resolve logic across five separate writers. The only win is read
  latency, which the batched single-call-per-page read already keeps small.
- **Per-row resolve at read (one application-service call per notification)**: rejected as
  the obvious N+1 on a 100-item page. The batched `summaries?ids=...` endpoint gives the same
  freshness for one round trip per page.
- **Embed company/job on the existing `applicationId` deep-link only (no card fields)**:
  rejected because it does not satisfy req 4, which needs the data on the card itself before the
  user navigates.
