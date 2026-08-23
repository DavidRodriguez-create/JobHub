# ADR 0015: Freeze the company logo (and name) on the application job-post snapshot

- **Status:** Proposed
- **Date:** 2026-06-29
- **Deciders:** jobhub-architect (story #244, sub-issue #255)
- **Affects:** application-service, notification-service, api-contracts, db/init, JobHub-ui

## Context

Story #244 ("BAD notifications") reports two defects on the notification card:

1. The job title is missing (the row shows the generic fallback label), even though the
   row still deep-links to the application.
2. The company chip is a text-initial icon, never the real company logo.

The card is rendered by `JobHub-ui` `NotificationIdentity.jsx` via
`resolveCardIdentity` (`notificationPresentation.js`), which sets
`resolved = Boolean(company && jobTitle)` and renders `FALLBACK_LABEL` when not
resolved. Tracing the read path end to end:

- notification-service `NotificationService.enrichWithApplicationSummaries` sets `company`
  and `jobTitle` together from the resolved `ApplicationSummary` (ADR 0014 enrich-at-read).
  They cannot independently go null at this layer.
- application-service `ApplicationService.resolveApplicationSummaries` -> `resolveJob(app)`
  builds `JobInfo(title, company, location, url)` from the crawled-job snapshot.
- **Root cause:** for crawled jobs, `resolveSnapshot(view)` persists the snapshot with
  `title`, `url`, `location` only. It never sets `company`, and it hashes company as `null`.
  Worse, the gateway port `JobPostGateway.JobPostView` (and `JobPostRemoteResponse`,
  `JobPostGatewayAdapter`) only carry `id, title, url, description, location`: job-service's
  `CompanyInfo { name, logoUrl }` is dropped at the application-service boundary. So the
  snapshot's `company` is structurally always null for crawled applications, which makes the
  UI `resolved` gate false and hides the (present) job title. The logo was never captured at all.

Constraints in scope: application-service and notification-service are **Hexagonal**; the
applied-job "copy" lives in `applications.job_post_snapshot` and is immutable per application
(crawled snapshots are frozen at apply time, deduped by `content_hash`). The DB schema is owned
by numbered `db/init/*.sql`; api-contracts is the single contract-first source of truth and
external/job-service models live there. job-service already exposes `CompanyInfo.logoUrl`
(`job-service.yaml`) as the source of truth for the logo.

## Decision

We will **capture both the company name and the company logo URL from job-service at apply
time and freeze them on the application snapshot**, then surface the logo through the existing
read paths. Concretely:

1. **api-contracts (FROZEN in this ticket):**
   - `application-service.yaml` `ApplicationSummaryResponse`: add `companyLogoUrl`
     (`string`, `format: uri`, `nullable: true`, `x-implementation-status: planned`).
   - `application-service.yaml` `JobSummary`: add the same `companyLogoUrl` so the user-facing
     application list/detail can also render the real logo from the same frozen source.
   - `notification-service.yaml` `NotificationResponse`: add `companyLogoUrl` (same shape),
     resolved at read time alongside `company`/`jobTitle` (ADR 0014 path).
   - No change to the apply **request** contract: `createApplication` already takes only
     `jobPostId` for crawled jobs and the service fetches the post server-side, so the logo is
     sourced internally via the outbound job-service client, not supplied by the client.

2. **db/init migration `031-applications-snapshot-company-logo.sql`** (number assigned below):
   add nullable `company_logo_url TEXT` to `applications.job_post_snapshot`. Nullable because
   manual entries, source posts without a logo, and pre-existing snapshots have no value. The
   `content_hash` dedup key is **not** changed by this migration (logo is not part of identity),
   but see the company-name follow-up note in Consequences.

3. **application-service (Hexagonal, build-out by developer):**
   - Extend the outbound boundary to stop dropping company: add `companyName` and
     `companyLogoUrl` to `JobPostGateway.JobPostView`, `JobPostRemoteResponse`, and the mapping
     in `JobPostGatewayAdapter` (reading job-service `company.name` / `company.logoUrl`).
   - `resolveSnapshot` sets `.company(view.companyName())` and `.companyLogoUrl(view.companyLogoUrl())`
     on the new `JobPostSnapshot` (domain), `JobPostSnapshotEntity` (`@Column(name = "company_logo_url")`),
     and `JobPostSnapshotMapper`.
   - `JobInfo` gains `companyLogoUrl`; `resolveJob` carries it through; `ApplicationSummaryView`
     gains `companyLogoUrl`; the internal summaries DTO mapping and `JobSummary` mapping populate it.

4. **notification-service (Hexagonal, build-out by developer):** thread `companyLogoUrl` through
   the `ApplicationSummary` domain model + gateway adapter + `NotificationResponse` mapping. No
   schema change in the notification schema (read-through only).

5. **JobHub-ui (build-out by frontend developer):** render the real logo image in `CoLogo`
   when `companyLogoUrl` is present, falling back to the existing initial chip when absent.
   Fix the `resolved` gate so a present `jobTitle` is shown even when `company` is null
   (the title-missing defect): the primary-label gate must not require `company`.

The jobTitle-missing defect and the logo defect are therefore **two owners**: the snapshot/
resolution fix (application-service) restores `company` so the existing gate passes for new
applications, and the UI `resolved`-gate fix makes the title robust to a null company for all
(including historical) rows.

## Consequences

- Positive: the snapshot becomes a faithful copy of the job post at apply time (company name +
  logo), matching its stated purpose. The logo and title render correctly without a live call
  back to job-service at read time. Both user-facing application views and notification cards
  benefit from one capture.
- Positive: the fix is backward-tolerant. The column is nullable, the contract fields are
  nullable/planned, and the UI degrades to the initial chip + (now) shows the title regardless
  of company, so historical rows with no captured logo/company still render acceptably.
- Negative / cost: a real data gap remains for **already-stored** snapshots: they have no
  company and no logo and will not be backfilled by this change. New applications are correct
  from the migration forward; old ones keep the initial chip. No backfill is in scope.
- Negative / cost: the outbound `JobPostView` widening means application-service now depends on
  `CompanyInfo` being populated by job-service; a null company there flows through as null
  (already handled).
- Follow-ups:
  - Developer build-out across application-service + notification-service + JobHub-ui per the
    plan above; tests: snapshot mapper unit (new column), resolveJob/summary unit, gateway
    adapter mapping (WireMock for the company/logo fields), notification enrich unit, and a UI
    test that a present jobTitle renders with a null company and that a present logo renders an image.
  - Open question deferred (not blocking): whether `company_name` should join the `content_hash`
    dedup key. Today company is excluded from the hash; since it was always null this was moot.
    Including it would split snapshots that differ only by company. Left as-is for this story to
    avoid churning the dedup semantics; revisit if company-only variance is observed.

## Alternatives considered

- **Resolve the logo live from job-service at notification read time** (no snapshot column) —
  rejected: violates the snapshot's "frozen copy at apply time" contract, adds an N+1 cross-
  service dependency on every notification page, and breaks if the source post is deleted or
  the job changes employer. The whole point of the snapshot is read-time independence.
- **Store only the logo, keep dropping the company name** — rejected: company is the actual
  root cause of the title-missing defect (the UI gate needs it, or the gate must change), and a
  logo without a company name is inconsistent with `CoLogo`'s initial fallback. Capture both.
- **Fix only the UI `resolved` gate, change no backend** — rejected as a complete fix: it
  addresses the title defect for all rows but cannot produce a real logo, which is the second
  requirement. The UI gate fix is kept as the historical-row safety net, paired with the
  backend capture for the logo.
- **Add the logo to the apply request body** — rejected: crawled applications are created from
  `jobPostId` and the server already fetches the post; trusting a client-supplied logo would
  bypass the source of truth and invite spoofing.
