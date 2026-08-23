# ADR 0026: Automatic company enrichment: crawler infers, job-service writes `source='derived'`

- **Status:** Superseded (by story #484, 2026-07-31)
- **Date:** 2026-07-25
- **Deciders:** jobhub-architect (David R H)

> **Superseded (#484, 2026-07-31).** The automatic-enrichment slice this ADR describes never
> ran successfully in any environment: crawler-service's `JOB_SERVICE_BASE_URL` was never wired
> into either compose file and defaulted to `http://localhost:8081`, which inside the crawler
> container resolves to crawler-service itself (not job-service), so every scheduled
> `CompanyEnrichmentScheduler` pass 404'd on `GET /internal/companies/pending-enrichment`.
> Rather than fix the wiring, #484 deletes the whole automatic LLM-inference slice
> (crawler scheduler + inference chain + gateway, job-service's two `/internal/companies/*`
> endpoints and their guarded derived write, the `PendingCompany` / `CompanyEnrichmentResult` /
> `CompanyEnrichmentStatus` contract models, and the `crawler.company.enriched_at` /
> `enrichment_attempts` tracking columns from `db/init/053`). Company facts (industry, size,
> headquarters, description, website) now come from a one-time curated seed
> (`db/init/054-company-data-seed.sql`) plus the retained admin manual-edit path
> (`PUT /jobs/admin/companies/{id}`, ADR 0025, unchanged). The `CompanyInfo` contract shape,
> the crawl-time company resolution reconciler (ADR 0023), and logo derivation (ADR 0024) are
> all unaffected. This ADR is retained for historical context only; nothing here is live.
- **Affects:** crawler-service (new outbound driver + LLM company-inference path), job-service
  (two internal X-Service-Key endpoints + guarded derived write + churn tracking),
  api-contracts (`job-service.yaml`: additive internal slice; `CompanyInfo` unchanged),
  db/init (`053`), podman compose mount lists, `.env` (`JOBHUB_INTERNAL_SERVICE_KEY` already
  exists). application-service, notification-service, auth-service, JobHub-ui: no change.

## Context

Story #354 (sub-issue #464), parent radar item "match: company intel (industry, size, HQ)".
The #426 company epic has shipped and this ADR does not reopen any of it:

- **#428 (ADR 0023):** `crawler.company` is a first-class entity **owned, modelled and written
  by job-service** (D1), even though it physically lives in the crawler schema. Identity is a
  slug (D3). The resolution reconciler (`CompanyResolutionService` to
  `CompanyPanacheRepository.upsertBySlug`) is **INSERT-only** (`INSERT ... ON CONFLICT (slug)
  DO NOTHING` then re-select; it never `UPDATE`s an existing row). `CompanyInfo` is **frozen**
  with `industry`, `size`, `headquarters`, `updatedAt` already present (D4); per-field
  provenance stays **internal**, only `manuallyEdited` and `updatedAt` are externally visible.
- **#429 (ADR 0024):** logo derived deterministically inside the same reconciler; the manual
  guard is structural (`WHERE ... manually_edited = false`).
- **#430 (ADR 0025):** admin *manual* enrichment is the **only writer of
  industry/size/headquarters today**, via `PUT /jobs/admin/companies/{id}`, setting
  `source='manual'`, `manually_edited=true`. A standing rule is restated there: **any future
  field-refresh `UPDATE` on an existing company MUST carry `WHERE manually_edited = false`.**

Verified in the tree, not re-derived:

- **No automatic path fills `industry/size/headquarters`.** The crawler has zero enrichment
  code for company facts. So the UI renders `—` for every company no admin hand-edited.
- **The LLM chain lives only in crawler-service.** `adapter/out/client/enrichment/` holds the
  config-driven provider chain (ADR 0004): Gemini to OpenAI-compatible to Ollama, per-model
  cooldowns, keys (`GEMINI_API_KEY`), and the `EnrichmentProviderFactory`. job-service has
  **none** of this and must not grow LLM keys or provider/cooldown logic.
- **Gemini free-tier RPD caps are tight** (per MEMORY: 2.5-flash-lite=20/day,
  3.1-flash-lite=500, gemma=1500; gemma has no JSON mode / no systemInstruction). Company
  enrichment must run **at most once per company** (not per job post) to stay in budget.
- **job-service owns the reconciler and is the sole writer.** `CompanyResolutionService`
  (`domain/service/`) + `CompanyResolutionScheduler` (`adapter/in/scheduler/`) already run a
  batched, config-guarded, off-the-read-path loop over `crawler.company`. job-service holds
  `SELECT, INSERT, UPDATE ON crawler.company` (051); the crawler holds no write grant it uses.
- **The internal X-Service-Key pattern already exists.** `application-service` exposes
  `/internal/*` operations guarded by `ServiceKeyFilter` (`adapter/in/rest/filter/`) and
  documented in `application-service.yaml` (`X-Service-Key` security scheme). notification-
  service *drives* cross-service loops by pulling work from and pushing results to these
  endpoints (`JOBHUB_INTERNAL_SERVICE_KEY`). This is the house pattern for one service
  orchestrating another's owned data.
- **crawler-service has no OpenAPI spec** in api-contracts (its `CrawlerResource` is
  hand-written). **job-service has one** (`job-service.yaml`). Adding a contract-first internal
  endpoint to job-service is additive to an existing spec; adding one to the crawler would mean
  a brand-new spec file.
- **`crawler.job_post` already models attempt tracking** with `enriched_at TIMESTAMPTZ` +
  `enrichment_attempts SMALLINT NOT NULL DEFAULT 0` + a partial pending index
  (`db/init/010-crawler.sql:140-158`). Mirror those names for `crawler.company`.
- **Highest committed migration is `052`** (`db/init/052-company-logo-backfill.sql` on
  origin/main, #429); #430 needed none. Next free number is **053**.
- crawler-service and job-service are both **Hexagonal** (`CLAUDE.md`). Nothing here changes
  that: each side is a mechanistic REST/persistence/outbound-client extension, not invariant-
  heavy domain behaviour.

Open questions this ADR closes: (1) where inference runs; (2) how derived values reach the
sole writer without breaking ADR 0023 D1; (3) provenance; (4) the manual-override and
crawl/derived precedence; (5) idempotency/churn under a tight RPD budget; (6) whether the
contract changes; (7) whether a migration is needed.

## Decision

### D1. Mechanism: reuse the crawler LLM chain. No paid third-party provider.

Automatic company enrichment reuses the **existing crawler provider chain** (ADR 0004:
Gemini to OpenAI-compatible to Ollama, per-model cooldowns, existing keys). We add a **new
company-inference path** (a new domain port + a company-facts prompt/parser) that **reuses the
same provider instances, factory and cooldown machinery** — it does **not** duplicate the
provider/cooldown logic and does **not** touch the domain port `JobEnricher`.

No paid third-party enrichment API is introduced. The "cost + rate-limit conversation" #428 and
#430 deferred is resolved here by staying on the free chain the crawler already runs, bounded by
D5. A paid provider would need an explicit cost decision on this ticket; none is justified for
three small fields on ~150 companies.

### D2. Service placement: crawler infers, job-service writes. Sole-writer invariant preserved.

The crawler **drives** the loop and performs the **LLM inference only**; job-service performs
**every write to `crawler.company`**, exactly as ADR 0023 D1 requires. This mirrors the
notification-service house pattern (a driver service pulls work from and posts results to
another service's `X-Service-Key`-guarded `/internal/*` endpoints).

Flow, per cycle:

```
crawler CompanyEnrichmentScheduler (@Scheduled, config-guarded, small batch, long cron)
  -> GET  job-service /internal/companies/pending-enrichment?limit=N   (X-Service-Key)
        job-service runs its LOCAL pending query (D5) and returns [{id, name, website}]
  -> for each pending company:
        crawler CompanyEnricher.infer(name, website)   (reuses ADR-0004 provider chain)
          -> on success: {industry?, size?, headquarters?}  (any field may be null)
          -> on all-providers-exhausted: throw -> status 'unavailable'
  -> POST job-service /internal/companies/{id}/enrichment  (X-Service-Key)
        body: CompanyEnrichmentResult { status: inferred|unavailable, industry?, size?, headquarters? }
        job-service performs the GUARDED write (D3/D4/D5). job-service is the ONLY writer.
```

Why this split rather than the alternatives (see Alternatives):

1. **The LLM concern stays wholly in crawler** — keys, provider chain, per-model cooldowns and
   the RPD budget live in one place. job-service never learns about providers, keys or
   exhaustion. It only receives an already-inferred result or an `unavailable` signal.
2. **The sole-writer invariant is untouched.** The crawler never opens the DB table; it POSTs
   values and job-service executes the guarded `UPDATE`. ADR 0023 D1 holds verbatim.
3. **The owner keeps the owned logic.** Pending selection, the override guard, provenance and
   churn tracking (all functions of `crawler.company`, which job-service owns) stay **inside
   job-service**, merely exposed over HTTP for the crawler to consume — identical to
   `application-service` exposing `GET /internal/applications/stale` for the notification loop.
4. **Contract-first stays cheap.** The new endpoints are additive to the **existing**
   `job-service.yaml`; no new crawler spec is needed, and the crawler consumes the generated
   models exactly as notification-service consumes job-service models.
5. **crawler already has the moving parts** — schedulers, outbound clients, WireMock. It gains
   one more outbound client (to job-service). **job-service gains no new outbound HTTP and no
   WireMock**: its two endpoints are inbound and are component-tested against the real
   DevServices DB with an `X-Service-Key` header.

The pending list carries only the LLM inputs (`id`, `name`, `website`) — never
`industry/size/headquarters`, never provenance — so the crawler cannot see or influence stored
values, only supply an inference for a name.

### D3. Provenance: `source='derived'`, internal only.

A successful inference that sets at least one of the three fields also sets `source='derived'`,
distinct from `crawl` (name/logo/slug only) and `manual` (admin). `source` stays **internal**
per ADR 0023 D4 — it is **not** added to `CompanyInfo`. The externally-visible signal remains
`manuallyEdited` + `updatedAt` (and `updatedAt` moves on a derived write, which is correct: the
row did change).

If the inference returns all-null (the LLM could not determine any field), `source` is left
unchanged (`crawl`) — there is nothing derived to record — but the attempt is still stamped
(D5) so the company is not retried.

### D4. Override and precedence: manual > derived > crawl. The guard is structural.

Precedence of writers to the three fields:

- **`manual` (admin, #430) is terminal.** `manually_edited=true` pins the whole row.
- **`derived` (this story) fills fields that `crawl` never sets.** `crawl` writes only
  `name`/`logo`/`slug`; `industry/size/headquarters` are null from `crawl`, so `derived` never
  contends with `crawl` on these three fields. There is no crawl-vs-derived conflict to arbitrate.

The manual-override invariant is enforced **structurally, twice**, per the ADR 0024/0025
standing rule:

1. **Excluded up front:** the pending query (D5) filters `manually_edited = false AND source <>
   'manual'`, so an admin-pinned company is never even offered to the crawler.
2. **Guarded at the write:** the derived `UPDATE` **always** carries `WHERE id = ? AND
   manually_edited = false`. Even a stale in-flight result (admin edits between the pending read
   and the POST-back) cannot overwrite a pinned row — the `UPDATE` matches zero rows and the
   result is dropped. This is the same record-level guard the logo backfill used.

Record-level (not per-field) provenance is retained per ADR 0025 D1: the only writes that can
touch an existing row are whole-row-guardable `UPDATE`s, so a single boolean is exactly
sufficient. This ADR does not add per-field provenance.

### D5. Idempotency and churn: `enriched_at` gates, `enrichment_attempts` bounds. Once per company.

Two new columns on `crawler.company` (D6) make enrichment run **at most once per company** and
never thrash:

- **`enriched_at TIMESTAMPTZ` (nullable)** — stamped when an inference **completes** (a
  parseable LLM answer, regardless of how many fields came back non-null). A stamped company is
  permanently excluded from the pending query, so a successful enrichment — even an all-null one
  — is never re-attempted. This is the churn guard.
- **`enrichment_attempts SMALLINT NOT NULL DEFAULT 0`** — incremented on every attempt,
  including `unavailable`. Bounds transient-failure retries.

job-service's **local** pending query (behind `GET /internal/companies/pending-enrichment`):

```sql
SELECT id, name, website
FROM crawler.company
WHERE manually_edited = false
  AND source <> 'manual'
  AND enriched_at IS NULL
  AND enrichment_attempts < :maxAttempts
  AND industry IS NULL AND size IS NULL AND headquarters IS NULL
ORDER BY created_at
LIMIT :limit;
```

The guarded writes behind `POST /internal/companies/{id}/enrichment`:

- **`status='inferred'`:**
  ```sql
  UPDATE crawler.company
     SET industry = :industry, size = :size, headquarters = :headquarters,
         source = CASE WHEN COALESCE(:industry,:size,:headquarters) IS NOT NULL
                       THEN 'derived' ELSE source END,
         enriched_at = now(), enrichment_attempts = enrichment_attempts + 1, updated_at = now()
   WHERE id = :id AND manually_edited = false;
  ```
- **`status='unavailable'`** (crawler chain exhausted — transient):
  ```sql
  UPDATE crawler.company
     SET enrichment_attempts = enrichment_attempts + 1, updated_at = now()
   WHERE id = :id AND manually_edited = false;
  ```
  `enriched_at` stays NULL, so the company is retried next cycle **until** `enrichment_attempts`
  reaches `:maxAttempts`, after which it drops out permanently (a name the LLM can never resolve
  stops costing calls).

**RPD budget.** ~150 companies, once each, fits inside a single day even on 3.1-flash-lite
(500/day) and comfortably on gemma (1500/day); the chain prefers the higher-cap models first.
The crawler's **existing per-model cooldown** (ADR 0004) means that once a model 429s, the rest
of the batch skips it and fails fast to `unavailable` **without** burning further RPD. Config
keeps the loop gentle: a small batch and a multi-hour cron drain the backlog in a day or two,
then the loop idles (every company is `enriched_at`-stamped). Frozen crawler config keys
(defaults, tune in prod):

```
crawler.company-enrichment.enabled=true
crawler.company-enrichment.batch-size=25
crawler.company-enrichment.cron=0 0 */4 * * ?
crawler.company-enrichment.max-attempts=5          # sent to job-service as the pending :maxAttempts
job-service side: reuses the value via the pending endpoint's limit + its own max-attempts config
```

job-service config keys (defaults):

```
job.company.enrich.max-attempts=5
```

### D6. Migration: one file, `db/init/053-company-enrichment-tracking.sql`.

**Assigned number: `053`** (highest committed is `052`). Exactly one file:

```sql
ALTER TABLE crawler.company ADD COLUMN enriched_at         TIMESTAMPTZ;
ALTER TABLE crawler.company ADD COLUMN enrichment_attempts SMALLINT NOT NULL DEFAULT 0;

CREATE INDEX idx_company_enrich_pending ON crawler.company (enriched_at)
    WHERE enriched_at IS NULL AND manually_edited = false;
```

- Column names/types mirror `crawler.job_post` (`010-crawler.sql`) for consistency.
- **No new grant.** `051` already granted `SELECT, INSERT, UPDATE ON crawler.company TO
  job_user` at table scope, which covers the two new columns. No new role, schema, password or
  `.env` key. `JOBHUB_INTERNAL_SERVICE_KEY` already exists for the X-Service-Key pattern.
- **No backfill.** Existing rows get `enriched_at = NULL`, `enrichment_attempts = 0`, which is
  exactly "pending", so the reconciler picks them up on its own. The three fact columns already
  exist from `051`.
- The `053` file must be **mounted by hand in both `podman-compose.yml` and
  `podman-compose.native.yml`** and added to the `CLAUDE.md` mount list (they drift; MEMORY:
  "compose init mounts lag migrations"). On an existing volume, hand-apply with
  `podman exec -i jobhub-db psql -U jobhub -d jobhub < db/init/053-company-enrichment-tracking.sql`
  then restart job-service so Hibernate `validate` passes against the new mapping.

### D7. Contract: `CompanyInfo` unchanged. Additive internal slice on `job-service.yaml`, frozen.

**`CompanyInfo` does NOT change.** `industry`, `size`, `headquarters` and `updatedAt` are
already frozen on it by #428 (ADR 0023 D4), all `nullable`, all rendered by the UI today. The
derived values flow into those existing fields; the UI needs no change to *display* them (it
already shows manual values). `enriched_at` and `enrichment_attempts` are **internal**
(provenance/bookkeeping, like `source`) and are deliberately **not** exposed — consistent with
ADR 0023 D4 keeping per-field provenance internal. No externally-visible field is added, so
there is **no `#330`-class cross-service blast radius** on `CompanyInfo`.

The one additive change is an **internal service-to-service slice** on the existing
`job-service.yaml`, all `x-implementation-status: planned`, tag `Internal`, secured by a new
`X-Service-Key` security scheme (copy the shape from `application-service.yaml`), **not** the
user `bearerAuth`:

- **`GET /internal/companies/pending-enrichment`** (`listPendingCompanyEnrichment`): query
  `limit` (min 1, max 100, default 25). `200` returns a JSON array of a **new slim schema
  `PendingCompany { id: uuid, name: string, website: string|null }`** (inputs only — no
  provenance, no fact fields). Errors `400`, `401` (missing/invalid `X-Service-Key`), `500`.
- **`POST /internal/companies/{id}/enrichment`** (`applyCompanyEnrichment`): body a **new
  schema `CompanyEnrichmentResult { status: enum[inferred,unavailable], industry: string|null
  (maxLength 80), size: string|null (maxLength 40), headquarters: string|null (maxLength 120)
  }`**. `204` on the guarded write (including the no-op case where the row is manually_edited or
  gone — the write matched zero rows, which is a success, not a 404, so a stale result is
  silently dropped). Errors `400`, `401`, `500`. The three field constraints match
  `CompanyUpdateRequest` (ADR 0025 D5) so producers stay uniform; `size` should stay in the
  documented headcount vocabulary but is not enum-constrained (same reason as `CompanyInfo`).

This slice is **frozen here.** The crawler consumes the generated `PendingCompany` /
`CompanyEnrichmentResult` models via its new outbound client (same mechanism as notification-
service consuming job-service models); the `@RegisterRestClient` interface stays in crawler.

## Where the pieces land

**job-service (Hexagonal), owner of the write:**

```
adapter/in/rest/filter/ServiceKeyFilter.java        NEW (mirror application-service): guards /internal/*
adapter/in/rest/InternalCompanyResource.java        NEW: implements the two generated internal ops
domain/port/in/EnrichCompaniesUseCase.java           NEW: List<PendingCompany> pending(int limit);
                                                          void applyEnrichment(id, result)  (or two ports)
domain/service/CompanyEnrichmentService.java         NEW: pending-selection + guarded derived write,
                                                          @Transactional, injects ports only
domain/port/out/CompanyRepository.java               EXTEND: findPendingEnrichment(limit, maxAttempts),
                                                          applyDerived(id, facts) [WHERE manually_edited=false],
                                                          markEnrichmentAttempted(id)  [unavailable path]
adapter/out/persistence/CompanyPanacheRepository     implement the new methods (native UPDATEs of D5)
adapter/out/persistence/entity/CompanyEntity.java    ADD @Column enriched_at, enrichment_attempts
domain/model/Company.java                             ADD enrichedAt, enrichmentAttempts (immutable)
application.properties                                job.company.enrich.max-attempts=5
db/init/053-company-enrichment-tracking.sql          NEW (D6)
api-contracts .../job-service.yaml                    additive internal slice (D7), planned
```

**crawler-service (Hexagonal), driver + inference:**

```
domain/port/out/CompanyEnricher.java                 NEW: CompanyFacts infer(String name, String website)
domain/model/CompanyFacts.java                        NEW: {industry?, size?, headquarters?} (immutable)
domain/port/in/EnrichCompaniesUseCase.java            NEW: int enrichPending(int limit)
domain/service/CompanyEnrichmentService.java          NEW: pull pending -> infer -> post back
adapter/in/scheduler/CompanyEnrichmentScheduler.java  NEW: @Scheduled, config-guarded (D5)
adapter/out/client/enrichment/... (reuse ADR-0004 chain) NEW company-facts prompt + parser +
                                                      a CompanyEnricher impl that reuses the existing
                                                      EnrichmentProviderFactory/provider instances/cooldowns
adapter/out/client/job/JobServiceInternalClient.java  NEW: @RegisterRestClient (or programmatic) with
                                                      X-Service-Key; GET pending, POST result;
                                                      references generated PendingCompany/CompanyEnrichmentResult
application.properties                                crawler.company-enrichment.* (D5) + rest-client base URL + key
```

Tests follow `CLAUDE.md`. **job-service:** unit tests for `CompanyEnrichmentService` (pending
filter incl. the manual/attempts/enriched_at predicates; derived write sets source only when a
field is non-null; the `WHERE manually_edited=false` no-op), mapper/entity tests for the two new
columns; component tests for the two `/internal/*` endpoints with the `X-Service-Key` header
against the real DevServices DB (happy + `401` missing key + the manual-edit no-op + the
all-null stamp). **No WireMock in job-service** (both endpoints are inbound). **crawler:** unit
tests for the company-facts parser and `CompanyEnrichmentService` (mock `CompanyEnricher` +
mock client: verify inferred vs unavailable POST bodies, verify it never sends fact fields it
did not infer); **WireMock** client tests for `JobServiceInternalClient` (crawler already uses
WireMock). No test-only prod seams; the crawler's inference is the real provider chain and fails
loudly to `unavailable` when exhausted (no enrichment mock in any prod profile).

## Consequences

- **Positive.** The sole-writer invariant (ADR 0023 D1) is preserved verbatim: the crawler
  never writes `crawler.company`; job-service performs every write. The LLM concern (keys,
  providers, cooldowns, budget) stays wholly in crawler; job-service grows no LLM dependency and
  no new outbound HTTP.
- **Positive.** No `CompanyInfo` change, so no cross-service contract blast radius. The UI shows
  derived values through the same fields it already renders for manual ones — zero UI work
  required for display (an optional follow-up could distinguish "derived" visually, but the
  contract deliberately does not surface it).
- **Positive.** The override guard is structural and doubly enforced (excluded from pending +
  `WHERE manually_edited=false` on the write), so a manual edit can never be clobbered, even by
  a stale in-flight result. Churn is bounded: `enriched_at` caps successful runs at one per
  company; `enrichment_attempts` caps transient retries.
- **Cost.** A new cross-service loop (crawler -> job-service internal endpoints) and a new
  crawler outbound client + company-facts prompt/parser. Contained: it reuses the existing
  provider chain and the existing X-Service-Key pattern; no new schema owner, role, or key.
- **Cost.** `source` is a single scalar and now takes a third value in the wild (`derived`).
  It remains internal and lossy-by-design (ADR 0023 D4 already accepted this); `manuallyEdited`
  stays the only exposed provenance.
- **Cost / operational.** Two migrations for the company line now depend on hand-mounting in
  both compose files (`051`, `052`, and now `053`); the CLAUDE.md list and both YAML mount lists
  must gain `053`. RPD tuning (`batch-size`, `cron`) is a prod-config concern, guided by D5.
- **Follow-ups, explicitly out of scope.** Re-enrichment/refresh of already-derived rows
  (would need a TTL and a refresh `UPDATE` that MUST carry `WHERE manually_edited=false`);
  surfacing "derived" vs "manual" in the UI; company-tag faceting; the sparse-company alert
  (#431); giving crawler-service a first-class OpenAPI spec.

## Alternatives considered

- **job-service runs the LLM itself (orchestrates locally, no crawler involvement).** Rejected:
  it would duplicate the ADR-0004 provider chain, cooldowns and keys into a second service, put
  `GEMINI_API_KEY` into job-service, and spread the RPD-budget concern across two services. The
  LLM capability belongs where it already lives.
- **job-service drives and calls a new crawler inference endpoint (crawler exposes the
  capability).** Viable and keeps keys in crawler, but rejected because the crawler has **no
  OpenAPI spec**, so a contract-first inbound endpoint would mean a brand-new crawler spec +
  `ServiceKeyFilter` on the crawler, and it would give job-service new outbound HTTP + WireMock.
  Placing the internal endpoints on the **existing** job-service spec (which the owner already
  needs for the guarded write) is strictly less surface, and it matches the notification-service
  house pattern where the *driver* pulls from the *owner*.
- **Crawler writes to its own staging table; job-service reconciles from staging.** Rejected:
  adds a second physical table and a second write path for zero gain over a direct guarded write
  behind an internal endpoint; more moving parts, same invariants to uphold.
- **Enrich per job post at crawl time (inside the existing job enrichment pass).** Rejected: it
  would infer the same company hundreds of times (once per posting), blowing the RPD budget and
  contradicting "at most once per company". Company enrichment is keyed on the company row, not
  the posting.
- **Expose `enrichedAt` on `CompanyInfo`.** Rejected: it is bookkeeping, not client-facing, and
  ADR 0023 D4 froze the externally-visible provenance to `manuallyEdited` + `updatedAt`. Keeping
  it internal avoids reopening the frozen contract for a field no consumer needs.
- **A paid third-party enrichment provider (Clearbit-style).** Rejected: no cost justification
  for three fields on ~150 companies when the free crawler chain, bounded by D5, suffices. Left
  as a future option if data quality proves inadequate, which would need its own cost ADR.
