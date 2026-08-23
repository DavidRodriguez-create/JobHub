# Decision records

Every non-obvious structural choice in JobHub is written down as an ADR before it is built.
New records start from [`0000-adr-template.md`](0000-adr-template.md) and take the next free
number.

!!! note "Numbering quirks"
    The sequence is not perfectly dense. There are **two 0009s** (ghosted alert and interview
    reminders, written in parallel), **no 0010**, and `0004-test-cases.md` is a companion test-case
    document rather than a decision. Numbers are never reused or renamed once merged, because
    other ADRs, commit messages and tickets link to them.

Grouped by area, newest first within each group.

## Crawling and enrichment

| ADR | Decision | Status |
|---|---|---|
| [0032](0032-crawler-shutdown-safe-scheduling.md) | Shutdown-safe crawler scheduling and trigger-run visibility | Proposed |
| [0029](0029-crawl-run-visibility.md) | Crawl-run visibility: log-level policy, per-target new counts, live progress | Accepted |
| [0021](0021-location-normalization.md) | Location normalization at crawl-write time | Accepted |
| [0017](0017-multiple-locations-per-job-post.md) | Multiple locations per job post | Accepted |
| [0016](0016-crawl-until-n-new-posts.md) | Crawl until N new posts, not N sources | Accepted |
| [0006](0006-cooperative-trigger-cancellation.md) | Cooperative cancellation for admin-triggered passes | Accepted |
| [0004](0004-config-driven-llm-provider-registry.md) | Config-driven LLM provider registry for enrichment ([test cases](0004-test-cases.md)) | Proposed |
| [0003](0003-admin-trigger-crawl-enrichment.md) | Admin-only trigger for crawl and enrichment passes | Accepted |

## Job search and companies

| ADR | Decision | Status |
|---|---|---|
| [0030](0030-per-user-saved-filters-and-comp-filter-removal.md) | Saved filters are per-user server state; compensation filter leaves the UI | Accepted |
| [0026](0026-automatic-company-enrichment.md) | Automatic company enrichment (crawler infers, job-service writes `derived`) | **Superseded** by story #484 |
| [0025](0025-company-admin-enrichment.md) | Admin company enrichment: manual-edit override and admin endpoints | Accepted |
| [0024](0024-company-logo-resolution.md) | Company logo resolution: job-service derives it | Revised (2026-07-25) |
| [0023](0023-company-first-class-entity.md) | Company as a first-class entity: schema ownership, identity slug | Accepted |
| [0020](0020-facet-cache-crawl-generation-invalidation.md) | Facet cache invalidated by a crawl-data generation stamp | Accepted |
| [0018](0018-job-search-count-estimate.md) | Best-effort totals: estimate above a threshold, short-TTL cache | Accepted |
| [0005](0005-job-post-query-performance.md) | Job-post query performance: indexes and full-text search | Accepted |
| [0001](0001-drill-down-facets.md) | Filter-aware (drill-down) facets for `GET /jobs/facets` | Accepted |

## Identity and security

| ADR | Decision | Status |
|---|---|---|
| [0028](0028-oauth-provider-availability-and-jit-names.md) | Unconfigured OAuth providers are a first-class state; just-in-time names | Accepted (amended) |
| [0027](0027-social-login-oauth.md) | Social login (Google, GitHub) via OAuth authorization-code | Accepted |
| [0022](0022-apply-profile-answer-bank.md) | Apply-profile answer bank in auth-service | Accepted |
| [0019](0019-admin-trigger-2fa-gate.md) | Admin trigger gated by the admin's own 2FA | Accepted |
| [0012](0012-totp-two-factor-authentication.md) | TOTP two-factor authentication | Accepted |
| [0002](0002-email-code-account-verification.md) | Email-code account verification (verify before login) | Accepted |

## Notifications and applications

| ADR | Decision | Status |
|---|---|---|
| [0031](0031-notification-category-taxonomy.md) | Notification category derived from notification type | Accepted |
| [0015](0015-application-snapshot-company-logo.md) | Freeze company logo and name on the application job-post snapshot | Proposed |
| [0014](0014-notification-card-company-job-enrich-at-read.md) | Notification cards carry company and job title via enrich-at-read | Accepted |
| [0013](0013-reminder-proxy-routing-collision.md) | Resolve the per-application reminders routing collision in the contract | Accepted |
| [0011](0011-custom-reminders-notification-service.md) | Custom reminders and the channel-gating fix | Accepted |
| [0009](0009-interview-reminders.md) | Interview reminders (24h + 1h): idempotency, internal endpoint, preferences | Proposed |
| [0009](0009-ghosted-alert-internal-status-update.md) | Ghosted alert via a service-key-authenticated internal status update | Proposed |
| [0008](0008-weekly-digest-email.md) | Weekly digest email: cross-service communication and design | Proposed |
| [0007](0007-notification-service.md) | notification-service: architecture and preferences API | Accepted |

!!! info "Status is the record's own claim"
    Several ADRs still read `Proposed` even though the work shipped: the status field was not
    always updated at merge. Treat the code and the [architecture overview](../overview.md) as
    authoritative for what is live, and the ADR as the reasoning behind it.
