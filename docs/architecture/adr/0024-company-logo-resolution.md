# ADR 0024: Company logo resolution: job-service derives it, crawler-service is not touched

- **Status:** Revised (2026-07-25): the runtime derivation below is dropped in favour of
  curated own-site icons. See "Revision" immediately below; the original decision is kept
  verbatim underneath as the record of what was tried and why it changed.
- **Date:** 2026-07-24 (original); 2026-07-25 (revision)
- **Deciders:** jobhub-architect (David R H)
- **Affects:** job-service (write path + backfill), api-contracts (`job-service.yaml`,
  `CompanyInfo.logoUrl` description clarified, status unchanged), db/init (`052`), JobHub-ui
  (fallback chip already exists), crawler-service (explicitly no change), application-service
  and notification-service (no change).

## Revision (2026-07-25): curated own-site icons, runtime derivation dropped (#451)

The original decision (D1/D2/D4) had job-service **derive** a best-effort logo URL from the
company slug and a configured logo CDN (`logo.clearbit.com/{compact-slug}.com`) on the insert
branch of the reconciler, with `db/init/052` doing the same in SQL. That is reversed for two
reasons found after acceptance:

- **The chosen CDN is gone.** The Clearbit Logo API was discontinued on **2025-12-01**, so
  every derived `logo.clearbit.com/...` URL now 404s. The feature shipped dead.
- **The slug guess was wrong for many companies.** `datadog` derives `datadog.com`, but the
  real domain is `datadoghq.com`; the same class of miss recurs across the seed. A best-effort
  guess against a domain we never verified is exactly the failure mode the maintainer rejected.

**Revised decision: logos are hardcoded, not derived, and point at each company's own-site
icon** (its `favicon.ico` / `apple-touch-icon.png`). No third-party logo API, no runtime
derivation, no downloads, no logo hosting, no licensing surface.

- **Write path.** `CompanyResolutionService` inserts a brand-new company with a **null**
  `logo_url` (`domain/model/CompanyLogo` and the two `job.company.logo.*` config keys are
  deleted). New/uncurated companies render the UI initials chip.
- **Backfill.** `db/init/052` becomes a **curated** map of `company_name` to own-site icon URL,
  each entry **verified to return a real image (HTTP 200)** before inclusion. It is keyed
  through a byte-for-byte copy of 051's slug mirror (so keys land on the exact stored slug),
  and keeps the same guard (`WHERE logo_url IS NULL AND manually_edited = false`) and
  idempotency. 138 companies are curated in this pass; the ~60 that could not be auto-verified
  (mostly large sites that bot-block the probe) stay null.
- **Filling the rest.** Uncurated companies, and any brand-new company that appears later, are
  filled by an **admin** via the company-edit screen. That makes this **depend on and sequence
  before #430 (US 4), which is the build-next story**; #431 (US 5) then surfaces the still-sparse
  companies. D3 (manual-edit guard) and D5 (crawler-service unchanged) are unaffected. D6 stands
  except the contract description now says "curated own-site icon", not "derived".

**Superseded by this revision:** D1 (derive in the reconciler), D2 (uniform CDN derivation),
D4's derivation formula (the migration is now a curated map, not a SQL mirror of a derivation).
Everything else in the original decision below still holds.

## Context

Story #429 (sub-issue #443), parent #426, US 3 of 5. US 2 (#428, ADR 0023) already made company
a first-class entity: `db/init/051-job-company.sql` created `crawler.company` with `logo_url`,
`source`, `manually_edited`, linked `crawler.pull_target.company_id`, and stood up a job-service
reconciler (`CompanyResolutionScheduler` to `CompanyResolutionService`, ADR 0023 D5) that resolves
each pull target to a stored company row. Every logo plumbing exists end to end, yet every
`crawler.company.logo_url` is null: `db/seeds/011-crawler-seeds.sql` sets `company_logo_url` in
zero rows. The visual half of #426 is therefore blank.

Binding constraints, not reopened here:

- **ADR 0023 D1: job-service is the sole writer of `crawler.company`.** crawler-service must not
  write company rows. Any logo the crawler could produce would still have to cross to job-service.
- **ADR 0023 D5: crawler-service was deliberately left unchanged**, with a documented reopen
  trigger: "Revisit when a source starts returning per-posting employer data."
- **Manual edits win (US 4, #430).** A crawl must never overwrite a company whose
  `manually_edited = true`.
- **No new production seam that exists only for tests** (`CLAUDE.md`; the "no production code for
  testing" rule). The logo derivation must be a pure function taking its inputs as parameters.
- job-service is **Hexagonal** and stays so: this is a mechanistic extension of an existing
  reconciler and one backfill, no new invariant-heavy domain behaviour.

**The decisive finding: no ATS the crawler already calls exposes a logo, and none exposes the
company's own web domain.** Verified live on 2026-07-24 against the exact endpoints the source
clients use, plus their board/company endpoints:

| ATS | Endpoint the client calls | Logo field? | Company domain? | URLs it returns |
|---|---|---|---|---|
| Greenhouse | `boards-api.greenhouse.io/v1/boards/{token}/jobs?content=true` (+ `/boards/{token}` returns only `name`, empty `content`) | none | none | `boards.greenhouse.io/{token}/...` (ATS-hosted) |
| Lever | `api.lever.co/v0/postings/{token}?mode=json` | none | none | `jobs.lever.co/{token}/...` (ATS-hosted) |
| SmartRecruiters | `api.smartrecruiters.com/v1/companies/{id}/postings` (posting `company` = `{identifier, name}` only) | none | none | `jobs.smartrecruiters.com/{id}/...` (ATS-hosted) |

The crawler's source clients (`GreenhouseJobSourceClient`, `LeverJobSourceClient`,
`SmartRecruitersJobSourceClient`) parse only job fields; none reads a logo or a career site that is
the company's own domain (all posting URLs are ATS-branded, so a favicon of them is the ATS logo,
not the employer's). The story's premise that each ATS "returns a company logo or a career-site URL
we can derive one from" does not hold against the live APIs. So a logo is **derived**, not fetched
from the source. ADR 0023 D4 already anticipated exactly this by wording the freeze "logo derived
in #429".

## Decision

### D1. Write path: job-service resolves the logo itself, in its existing reconciler. crawler-service is NOT changed.

We will have **job-service** derive the logo, inside the **existing** `CompanyResolutionService`
(ADR 0023 D5), which is already the sole writer of `crawler.company` and already holds everything
the derivation needs (`pull_target.company_name`, the computed slug). There is **no crawler change,
no new outbound HTTP call, and no new internal `X-Service-Key` endpoint.**

This is the "job-service resolving it itself" option, chosen because the alternatives are strictly
worse given the finding above: the crawler has no logo to hand over (it would have to make a *new*
external call to a logo service that it is told not to add), and it cannot write `crawler.company`
(ADR 0023 D1), so it would additionally need a new internal push endpoint on job-service. Deriving
in job-service's existing reconciler removes all three moving parts.

Concretely (job-service, Hexagonal):

- New pure domain function `domain/model/CompanyLogo.java`,
  `static Optional<String> deriveUrl(String slug, String logoCdnBaseUrl)`. Pure, no CDI, no clock,
  inputs as parameters. It maps the company slug to a candidate web domain (strip separators,
  append the configured TLD, default `.com`) and formats the configured logo-CDN URL template.
  For example `stripe` to `https://logo.clearbit.com/stripe.com`,
  `delivery-hero` to `https://logo.clearbit.com/deliveryhero.com`.
- `CompanyResolutionService.resolvePending()` sets `logoUrl` on the `Company` it builds **only on
  the insert branch** (`upsertBySlug`), so a newly appearing pull target's company is born with a
  best-effort logo. It never updates an existing company row, so it cannot overwrite anything.
- Config (job-service `application.properties`, sensible defaults):
  `job.company.logo.cdn-base=https://logo.clearbit.com/` and `job.company.logo.tld=com`. Making the
  CDN a config value means swapping providers (see D4 risk) is an ops change, not a contract change.

### D2. Logo source per ATS: none. Derive uniformly from the company domain.

There is no per-ATS logo field, so there is no per-ATS branch. The same deterministic derivation
(slug to candidate domain to logo-CDN URL) applies to Greenhouse, Lever, SmartRecruiters and every
other source. This is the honest, evidence-based answer to "which field per ATS": the field does
not exist on any of them, so uniform domain derivation is the design.

### D3. Manual-edit guard: two enforcement points, both structural.

- **Crawl / reconcile path.** The reconciler only ever **inserts** a company (via `upsertBySlug`,
  `INSERT ... ON CONFLICT (slug) DO NOTHING`); it performs no `UPDATE` of an existing company. It
  therefore cannot overwrite a `manually_edited = true` row by construction. If a future story adds
  a logo-*refresh* `UPDATE` on existing companies, that statement MUST carry
  `WHERE manually_edited = false`; this is recorded as a standing rule.
- **Backfill path.** The backfill `UPDATE` (D4) carries
  `WHERE logo_url IS NULL AND manually_edited = false`, so it skips both manual rows and any row
  that already has a logo. This is where the guard is enforced today, in SQL.

### D4. Backfill: owned by job-service (#447), one idempotent `db/init` migration, number 052.

We will add **`db/init/052-company-logo-backfill.sql`**, owned by job-service (ticket #447). It:

1. Sets `logo_url` on `crawler.company` for the ~150 existing rows using a **SQL mirror** of the D1
   derivation (compact the slug, append the TLD, format the CDN URL), so the historical companies
   get logos without waiting a full reconciler cycle. Same pattern, and same "Java stays canonical"
   caveat, as the 051 slug mirror: the reconciler only touches NULL rows, so any drift can at worst
   give a future company a slightly-wrong logo URL, never lose or corrupt data.
2. Guards with `WHERE logo_url IS NULL AND manually_edited = false` (D3), which also makes it
   **idempotent and safe to re-run**: a second run matches nothing because the first run set the
   URLs. It never touches manual rows.
3. Ends with a verification `SELECT` of `(total, with_logo)` counts so the #437-style devops check
   is mechanical.
4. Needs **no new grant**: job_user already has `UPDATE ON crawler.company` from 051.
5. Must be mounted by hand in both `podman-compose.yml` and `podman-compose.native.yml` and added
   to the `CLAUDE.md` mount list (they drift; missed on #336 and #336-era stories).

No `db/init-users.sh` change, no new role, schema, password or `.env` key.

### D5. Migration ranges.

- **job-service (#447): `052`** (this backfill). `053` reserved for job-service if the reconciler
  change needs a follow-up migration.
- **crawler-service (#446): none.** crawler-service has no DB change and no code change under this
  decision (D1), consistent with ADR 0023 D5's "crawler is not changed" until an aggregator source
  appears. This is not that trigger.

### D6. Contract: `CompanyInfo.logoUrl` stays `existing`; description clarified, no new surface.

`logoUrl` is already frozen `x-implementation-status: existing` on `CompanyInfo`
(`api-contracts/.../job-service.yaml`). No new operation, no new model, no internal endpoint is
introduced. The only spec edit is to the `logoUrl` **description**, to record that the value is a
best-effort derived URL that may 404 and that the client degrades to its fallback chip on a broken
image. `BACKEND_GAPS.md` availability is unchanged (`logoUrl` was already exposed, merely null).

## Consequences

- **Positive.** Zero blast radius outside job-service: no crawler change, no new HTTP call, no new
  internal endpoint, no contract-shape change, no new DB grant/role. The write path stays inside the
  single ADR-0023 owner of `crawler.company`.
- **Positive.** The manual-edit guard is structural, not merely a runtime check: the reconciler
  physically cannot overwrite (insert-only), and the backfill's `WHERE` clause skips manual rows.
- **Positive.** Acceptance is met by construction: the backfill gives the seeded majority a logo
  immediately; new companies get one at resolution; misses degrade to the existing `CoLogo` initials
  chip (`JobHub-ui/src/components/ui.jsx`), which already renders on image error.
- **Cost / risk.** The derived URL is **unverified**, so a share of companies get a `logoUrl` that
  404s (`datadog` to `datadog.com` misses `datadoghq.com`). This is accepted: the story blesses a
  best-effort URL plus graceful UI fallback and puts URL hosting/verification out of scope. It does
  weaken the contract's "null means no logo" to "null means no derivable domain"; the spec text is
  updated to say so.
- **Cost / risk.** **Hotlinking + provider dependency.** We reference a third-party logo CDN
  (Clearbit Logo API) directly. Clearbit's free logo API has an uncertain future post-HubSpot
  acquisition. Mitigated by making the CDN base a config value (D1): DuckDuckGo
  (`icons.duckduckgo.com/ip3/{domain}.ico`) or Google favicon
  (`www.google.com/s2/favicons?domain={domain}&sz=128`) can replace it without a code or contract
  change. Google favicon returns a generic globe on miss (never 404s), so it is a worse fit for the
  fallback rule and is the second choice, not the first. Self-hosting/proxying logos is out of scope
  (story #429) and a candidate future ADR.
- **Cost.** The derivation rule now exists twice for one migration (Java in the reconciler, SQL in
  the backfill), the same duplication 051 already carries. Contained by "the reconciler only touches
  NULL company_id" and called out in the migration header.
- **Orchestration consequence.** Under D1/D5, **build ticket #446 (crawler-service) has no work**:
  no code, no migration. Its scope folds entirely into #447. This is surfaced to the orchestrator
  to close or repurpose #446; the architect does not close tickets.

## Alternatives considered

- **Crawler resolves the logo at crawl time and pushes it to job-service.** Rejected: the live ATS
  APIs carry no logo and no company domain (table above), so the crawler would have to make a *new*
  external call (the story says do not) and, because it cannot write `crawler.company` (ADR 0023
  D1), would need a *new* internal push endpoint on job-service. Three new moving parts for a value
  job-service can derive itself from data it already holds.
- **A new internal `X-Service-Key` endpoint on job-service that the crawler calls with the logo.**
  Rejected for the same reason plus it widens the frozen contract surface for zero gain over D1.
- **Reconciler UPDATEs existing companies to refresh logos.** Deferred: not needed for this story
  (the backfill covers existing rows once), and it would introduce an `UPDATE` path that must carry
  the manual-edit `WHERE` guard. Insert-only keeps the guard structural.
- **Google/DuckDuckGo favicon service instead of a logo CDN.** Kept as the documented config-swap
  fallback, not the default: Google favicon never 404s (returns a globe), which defeats the
  "dead link degrades to the fallback chip" acceptance.
- **Self-host / proxy logos.** Out of scope per story #429 (hosting is explicitly excluded);
  revisit as its own ADR if hotlinking or provider deprecation forces it.
