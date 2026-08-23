# ADR 0023: Company as a first-class entity: schema ownership, identity slug, frozen contract

- **Status:** Accepted
- **Date:** 2026-07-23
- **Deciders:** jobhub-architect (David R H)
- **Affects:** job-service, api-contracts (`job-service.yaml`, frozen here), db/init (`051`),
  JobHub-ui (consumer change), crawler-service (no change), application-service (no change),
  notification-service (no change), podman compose mount lists

## Context

Story #428 (sub-issue #432), parent #426. The product decision is already made and is not
reopened here: store a real company entity as the source of truth, do not hardcode company data
in the DB, do not do a live third-party lookup on the read path.

Facts verified in the tree, not re-derived:

- **Company today is two denormalised columns on `crawler.pull_target`:** `company_name`
  (NOT NULL) and `company_logo_url` (`db/init/010-crawler.sql:10-11`). job-service reads them
  through a joined `PullTargetEntity` and builds `domain/model/Company` in
  `adapter/out/persistence/mapper/JobPostMapper` (name + logoUrl, nothing else).
- **The contradiction to resolve.** Story #428 says the new table is "owned by job-service
  (`job` schema)", but the `job` schema is **empty**: `db/init` creates 23 tables, none of them
  in `job`. All six job-service entities map `schema = "crawler"`, and job-service already
  **owns three tables that physically live in the crawler schema** (`saved_job`, `saved_filter`,
  `trigger_request`), with explicit least-privilege grants at `db/init/010-crawler.sql:214-216`.
  `quarkus.hibernate-orm.database.default-schema=job` in `application.properties` is therefore
  aspirational today, and the test profile overrides it to `crawler` anyway.
- **The search path already fetches the pull target.**
  `JobPostPanacheRepository:65` builds `SELECT j FROM JobPostEntity j LEFT JOIN FETCH j.target t`,
  so company data reached through `pull_target` costs no extra query and no N+1 (the anti-N+1
  work of #333/#406 stays intact).
- **The company facet and the company filter group by `t.companyName`**
  (`JobPostPanacheRepository:136-137`, `:461`, `:610`), and `crawler.saved_filter` persists
  serialised `FilterValues` that contain those exact company-name strings.
- **Every pull target is a single-employer ATS board.** The 234 seeded targets are greenhouse
  (177), workday (29), lever (22), smartrecruiters (4) and amazon (2). There is no aggregator
  source where one target yields postings from many employers.
- **`CompanyInfo` is a shared api-contracts type** with four consumers (see Blast radius).
  Story #330 taught that a shared response-type change passes a scoped `mvn -pl` build and only
  a full-reactor build catches the breakage.
- **`text[]` lesson (#407):** an array column whose JPA mapping drifts from prod's `text[]` to
  Hibernate's inferred `varchar[]` hides array-operator bugs behind green tests.
- Highest existing migration: `db/init/050-auth-apply-profile.sql`.
- job-service is **Hexagonal** per `CLAUDE.md`. Nothing in this story changes that: the feature is
  mechanistic (one new stored aggregate, one resolution rule, one read projection), the domain does
  not grow invariant-heavy behaviour, and there is a near 1:1 mapping between endpoints and use
  cases. **job-service stays Hexagonal.**

## Decision

### D1. Physical schema: `crawler.company`, owned by job-service

We will create the table as **`crawler.company`**, owned (written, migrated, modelled) by
**job-service**, exactly like `crawler.saved_job`, `crawler.saved_filter` and
`crawler.trigger_request` already are.

"Owned by job-service" is about who writes it and who models it, not about which schema string
it carries. Putting it in `job` instead would buy nominal isolation (same database, same
connection, same transaction) and cost a cross-schema foreign key from a crawler-owned table to a
job-owned one, which inverts the ownership direction of the FK and complicates the grant and
drop/recreate lifecycle for no functional gain. Company is a satellite of `pull_target` and
`job_post`: it is joined with them in every single search query, so it belongs next to them.

Follow-up recorded, not done here: `job` remains an empty schema and
`quarkus.hibernate-orm.database.default-schema=job` remains misleading. That is a pre-existing
inconsistency; fixing it is a repo-wide move (six entities, three tables, all grants) and is not
this story's job.

### D2. Link: `pull_target.company_id`, and `job_post` reaches company through its existing FK

We will add **one nullable column, `crawler.pull_target.company_id UUID REFERENCES
crawler.company(id)`**. We will **not** add a column to `crawler.job_post`.

`job_post` references company through the FK it already has: `job_post.target_id` is **NOT NULL**
and references `pull_target(id)`, and every pull target denotes exactly one employer. The
resolution path is therefore total by construction:

```
job_post.target_id (NOT NULL) -> pull_target.company_id -> company
```

Why the link sits on `pull_target` rather than on `job_post`:

1. **Correctness of the backfill.** `company_name` is NOT NULL on every pull target, so setting
   `company_id` on ~234 rows makes 100% of existing postings resolve a company. The alternative
   backfills a column on the hottest, largest table in the system.
2. **New postings need no work at all.** With the link on `job_post`, every newly crawled post
   arrives with `company_id` NULL (thousands per crawl), which forces either a crawler change or a
   permanent per-post resolver. With the link on `pull_target`, a post inherits its target's
   company the moment it is inserted, and only a brand new pull target (rare, admin or seed driven)
   ever needs resolving. This is what makes D5 (no crawler change) true.
3. **Zero query cost.** The search query already does `LEFT JOIN FETCH j.target t`; company is one
   more `LEFT JOIN FETCH t.company` onto a ~234 row table, on the same statement. No extra query,
   no N+1, no change to the facet/count caches.
4. **Narrower privilege.** job-service needs `UPDATE (company_id)` on `pull_target` (one column on
   a small config table) instead of `UPDATE` on `crawler.job_post`, the crawler's core write table.

Forward compatibility, deliberately not built now: if an aggregator source is ever added (one pull
target, many employers), add a nullable `job_post.company_id` **override** that wins over the
target's company. Because the domain resolves company inside the mapper, that change touches the
mapper and the repository join only. `CompanyInfo` does not change.

### D3. Identity: the slug rule

Company identity is a **normalised slug** computed from the company name, unique in the table
(`uq_company_slug`). Two employers arriving from two different ATS sources collapse onto one row
if and only if they produce the same slug.

The rule is a **pure function** with the raw name as its only input (no CDI, no clock, no
production seam that exists only for tests, per `CLAUDE.md`). Canonical home:
`job-service/.../domain/model/CompanySlug.java`, `static Optional<String> of(String rawName)`.

Steps, applied in this exact order (this ordering is load bearing: QA cases assert it):

| # | Step | Example |
|---|---|---|
| S0 | Null or blank after trimming Unicode whitespace (including NBSP U+00A0, tab, newline): **no slug**, `Optional.empty()` | `"   "` to empty |
| S1 | Unicode normalise to **NFKC** (folds full-width and compatibility forms) | `"Ｓｔｒｉｐｅ"` to `"Stripe"` |
| S2 | Remove every parenthesised or bracketed segment **and its content**: `(...)`, `[...]`, `{...}` | `"Block (Square)"` to `"Block"` |
| S3 | Replace `&` with `" and "` | `"H&M"` to `"H and M"` |
| S4 | Drop `/` when it sits between two single letters (slash legal forms) | `"A/S"` to `"AS"` |
| S5 | Drop a trailing internet suffix `.com .io .ai .co .net .org` when it directly follows a word character (once) | `"Booking.com"` to `"Booking"` |
| S6 | Delete the characters dot, comma, straight quote, curly quote (U+2019), backtick, acute accent (U+00B4) and double quote, with **no** separator | `"S.A."` to `"SA"`, `"O'Reilly"` to `"OReilly"` |
| S7 | Strip diacritics: NFD decompose, drop combining marks, then map what NFD does not decompose: `ø/Ø`>o, `đ/Đ`>d, `ð/Ð`>d, `þ/Þ`>th, `ł/Ł`>l, `æ/Æ`>ae, `œ/Œ`>oe, `ß`>ss, `ı`>i, `ħ`>h, `ŧ`>t, `ĸ`>k | `"Nestlé"` to `"Nestle"`, `"Ørsted"` to `"Orsted"` |
| S8 | Lowercase with **`Locale.ROOT`** (never the default locale: with a Turkish default `"INDITEX"` lowercases to `"ındıtex"` and the slug silently forks) | `"NESTLE"` to `"nestle"` |
| S9 | Every character outside `[a-z0-9]` becomes a separator; collapse runs to a single `-`; trim leading/trailing `-`. Non-ASCII letters that survived S7 (Cyrillic, Greek, CJK) are dropped here | `"Grupo Planeta"` to `"grupo-planeta"` |
| S10 | Split on `-`. While the **last** token is in the legal-form set (below) or is a connector (`and`, `und`, `y`, `et`, `e`) **and at least one token would remain**, drop it. Repeat | `"Muller GmbH and Co KG"` to `"muller"` |
| S11 | Truncate to **120 characters at a token boundary** (drop whole trailing tokens, never cut mid-token) | |
| S12 | Result must match `^[a-z0-9]+(-[a-z0-9]+)*$`. If nothing survives, **no slug**: no company row is created, and the response falls back to the crawl-time name (log at WARN) | `"楽天"` to empty |

Legal-form set for S10 (closed list, lowercase, post S9):

```
ab, ag, aps, as, asa, bhd, bv, cia, co, company, corp, corporation, cv, doo, gk, gmbh,
inc, incorporated, kft, kg, kk, llc, llp, ltd, ltda, limited, nv, oo, oy, oyj, plc, pt,
pte, pty, sa, sarl, sas, sau, sdn, se, sl, slu, sp, spa, sprl, srl, ug, z, zrt
```

Deliberately **not** in the set: `group`, `holding`, `holdings`, `international`, `labs`,
`technologies`, `solutions`. Stripping those over-merges genuinely different legal entities and
brand lines. Two names differing by such a word are two companies until an admin says otherwise
(story #430).

Worked examples the QA engineer can lift verbatim:

| Raw name | Slug |
|---|---|
| `Stripe` / `  STRIPE  ` / `Stripe, Inc.` | `stripe` |
| `Nestlé S.A.` / `NESTLE SA` / `Nestle` | `nestle` |
| `Block (Square)` | `block` |
| `On (On Running)` | `on` |
| `Booking.com B.V.` | `booking` |
| `Ørsted A/S` | `orsted` |
| `H&M` / `H and M` | `h-and-m` |
| `Zalando SE` | `zalando` |
| `Grupo Planeta, S.L.` | `grupo-planeta` |
| `Müller GmbH & Co. KG` | `muller` |
| `Allegro Sp. z o.o.` | `allegro` |
| `İŞBANK` | `isbank` |
| `Co` | `co` (S10 guard: never strip to empty) |
| `Amazon Web Services` | `amazon-web-services` (deliberately **not** merged with `amazon`) |
| `楽天` | no slug, no row, falls back to the crawl-time name |

Residual risk, accepted and documented: S10 can merge two genuinely different employers when one
name equals another's name plus a legal suffix (`Software AG` and a hypothetical `Software` both
slug to `software`). The merge is visible in the UI (one card, one name) and is repaired by an
admin rename in story #430. The opposite failure (the same employer forking into two rows) is the
one this story exists to prevent, so the rule is tuned to merge.

Concurrency: resolution is `SELECT by slug`, then `INSERT ... ON CONFLICT (slug) DO NOTHING`, then
re-select. Never `SELECT` then blind `INSERT`.

### D4. The frozen `CompanyInfo` contract

Frozen in this change at `api-contracts/src/main/resources/openapi/job-service.yaml`. Twelve
properties: `id`, `slug`, `name`, `logoUrl`, `website`, `industry`, `size`, `headquarters`,
`description`, `tags`, `manuallyEdited`, `updatedAt`. `name` and `logoUrl` keep
`x-implementation-status: existing`; every new property is `planned`.

Rules baked into the spec text:

- **Nullability.** Every property except `name` is `nullable: true`. An unknown value serialises as
  `null`, never `""`, never `"-"`. `openApiNullable=false` in `api-contracts/pom.xml`, so
  `nullable: true` is pure documentation: the generated model keeps plain Java types and no
  `JsonNullable` wrapper appears (verified against the generated `CompanyInfo`).
- **`tags` is frozen now**, empty of behaviour: nothing populates it in #428 (it is filled by
  #430), and the service never emits `[]`. Null and "no tags" are the same state, so no consumer
  needs an empty-array special case. Filtering or faceting by tag stays out of scope.
- **Projection rule.** `GET /jobs` (JobPostSummary, the card list) leaves the heavy company
  `description` null; `GET /jobs/{id}` (JobPostResponse) populates it. This mirrors story #330,
  which removed the job's own `description`/`requirements` from list payloads. Embedding a prose
  company description in each of 20 cards would undo that work.
- **Provenance that is externally visible: `manuallyEdited` and `updatedAt` only.** The DB also
  stores a `source` column, kept internal, because provenance is per field in practice (name from
  the ATS, logo derived in #429, description admin-edited in #430) and a single scalar would lie.
  If story #430 needs per-field provenance it adds an admin-only schema, it does not reopen
  `CompanyInfo`.
- **`industry` and `size` are strings, not enums.** Values arrive from heterogeneous sources and
  from admin edits; a frozen closed list breaks every consumer the first time a new value appears
  (the generated Jackson enum throws on unknown values). `size` carries a documented recommended
  vocabulary instead: `1-10`, `11-50`, `51-200`, `201-500`, `501-1000`, `1001-5000`, `5001-10000`,
  `10000+`.
- **`id` and `slug` are both exposed.** `id` is the immutable handle admin operations (#430) and
  alerting (#431) address; `slug` is the stable grouping key the UI should use instead of deriving
  one client side. Both are null while a posting's company is unresolved (the fallback window
  described in D5), which is exactly when the response carries only `name` and `logoUrl`.

### D5. Resolution path: job-service reconciler, plus fallback. **crawler-service is NOT changed.**

**Explicit call: NO. crawler-service needs no change in this story.** Reasons:

1. A newly crawled `job_post` inherits its target's company through `target_id` (D2), so a new
   posting resolves a company the instant it is inserted, with no crawler code involved.
2. Company data is not produced per posting by any current source client: all five source types
   are single-employer boards and the employer is already recorded on the pull target.
3. The only gap is a **brand new pull target** with `company_id` still NULL. That is rare (seed or
   admin driven), and it is covered twice over: by the reconciler below, and, until the reconciler
   next runs, by the mapper fallback that reads the denormalised `pull_target.company_name` /
   `company_logo_url` exactly as today. **No posting ever loses its company name, at any moment.**

Resolution therefore lives in job-service as a small scheduled reconciler
(`adapter/in/scheduler/`, the hexagonal home for `@Scheduled`): find pull targets with
`company_id IS NULL`, slug the name, upsert the company, assign the id. Batched, idempotent,
config-guarded, off the read path (no writes during a GET).

Crawler work becomes necessary only if an aggregator source is added, which is the same trigger as
the `job_post.company_id` override in D2. If the user wants richer company data captured at crawl
time (ATS payloads sometimes carry a website or a description), that is story #429/#430 territory
and deserves its own ticket, not a widening of this one.

### D6. Migration: `db/init/051-job-company.sql`

**Assigned number: `051`.** Exactly one new file for this story. It must contain, in order:

1. `CREATE TABLE crawler.company`:
   `id UUID PK DEFAULT gen_random_uuid()`, `slug TEXT NOT NULL`, `name TEXT NOT NULL`,
   `website TEXT`, `industry TEXT`, `size TEXT`, `headquarters TEXT`, `description TEXT`,
   `logo_url TEXT`, `tags TEXT[]`, `source VARCHAR(16) NOT NULL DEFAULT 'crawl'`,
   `manually_edited BOOLEAN NOT NULL DEFAULT FALSE`,
   `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`.
   Constraints: `uq_company_slug UNIQUE (slug)`,
   `chk_company_source CHECK (source IN ('crawl','derived','manual'))`,
   `chk_company_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')`.
   **`tags` is `TEXT[]`**: the #407 lesson applies the day anything filters on it (an
   array-overlap filter needs an explicit `text[]` cast, and the JPA mapping must pin
   `columnDefinition = "text[]"` so the drop-and-create test schema cannot drift to `varchar[]`).
2. **No `updated_at` trigger.** job-service is the only writer and sets `updated_at` on write, so
   the DevServices drop-and-create test schema behaves identically to prod. This deliberately
   differs from `crawler.pull_target`, where the trigger exists because job-service is not the
   writer.
3. `ALTER TABLE crawler.pull_target ADD COLUMN company_id UUID REFERENCES crawler.company(id)`.
   No new index: `pull_target` is ~234 rows and the join uses `company`'s primary key.
4. **Backfill**, in one transaction: insert one `crawler.company` row per distinct slug computed
   from `pull_target.company_name` (carrying `name` from a deterministic pick, for example the
   longest or first name in the group, and `logo_url` from the first non-null `company_logo_url`
   in the group, `source = 'crawl'`), then `UPDATE crawler.pull_target SET company_id = ...` for
   every row. Every pull target must end with a non-null `company_id`; end the file with a
   verification `SELECT` of the two counts so the devops check on #437 is mechanical.
   The SQL slug expression mirrors D3 for the data that exists (all current names are Latin;
   `unaccent` covers S7). The **Java rule stays canonical**: the reconciler only ever touches
   targets whose `company_id` is NULL, so a divergence between the SQL mirror and the Java rule can
   at worst create a duplicate row for a future target, never a lost or renamed company.
5. **Grants (this is the entire least-privilege impact):**
   `GRANT SELECT, INSERT, UPDATE ON crawler.company TO job_user;` and
   `GRANT UPDATE (company_id) ON crawler.pull_target TO job_user;`. No DELETE anywhere (deleting a
   company would orphan the link; merges are #430 territory). **No change to `db/init-users.sh`:**
   no new role, no new schema, no new password, nothing for `.env` or the compose files beyond
   mounting the file. `crawler_user` picks up DML on `crawler.company` automatically from the
   `ALTER DEFAULT PRIVILEGES` in `001-schemas.sql`; that is unused but harmless and already true of
   `crawler.saved_job`. Hand-apply the migration as the same superuser
   (`podman exec -i jobhub-db psql -U jobhub -d jobhub < db/init/051-job-company.sql`) so the
   default privileges match a fresh volume.
6. Mount the file **by hand in both `podman-compose.yml` and `podman-compose.native.yml`** (line
   115 area) and in the `CLAUDE.md` mount list. They drift; this was missed on #336. That is
   sub-issue #437.

## Where the pieces land (job-service, Hexagonal)

```
domain/model/Company.java              extend: id, slug, name, website, industry, size,
                                       headquarters, description, logoUrl, tags,
                                       manuallyEdited, updatedAt  (@Getter @Builder, immutable,
                                       zero framework annotations)
domain/model/CompanySlug.java          NEW, pure: static Optional<String> of(String rawName)
domain/port/out/CompanyRepository.java NEW: Optional<Company> findBySlug(String),
                                       Company upsertBySlug(Company)
domain/port/out/PullTargetRepository   NEW: List<UnresolvedTarget> findWithoutCompany(int limit),
                                       void assignCompany(UUID targetId, UUID companyId)
domain/port/in/ResolveCompaniesUseCase NEW: int resolvePending()
domain/service/CompanyResolutionService implements the use case, injects ports only,
                                       @Transactional per batch

adapter/in/scheduler/CompanyResolutionScheduler   NEW, @Scheduled, config-guarded
adapter/out/persistence/entity/CompanyEntity      NEW, @Table(name="company", schema="crawler"),
                                                  explicit @Column(name="snake_case"),
                                                  tags: columnDefinition="text[]" + @JdbcTypeCode(ARRAY)
adapter/out/persistence/entity/PullTargetEntity   add @ManyToOne(LAZY) company + company_id
adapter/out/persistence/mapper/CompanyMapper      NEW, entity to domain
adapter/out/persistence/CompanyPanacheRepository  NEW, implements the port + PanacheRepositoryBase
adapter/out/persistence/JobPostPanacheRepository  search JPQL gains LEFT JOIN FETCH t.company
adapter/out/persistence/mapper/JobPostMapper      company from target.company, else fall back to
                                                  target.companyName/companyLogoUrl (name+logo only)
adapter/in/rest/dto/JobPostResponseMapper         map all 12 properties, blank strings to null
adapter/in/rest/dto/JobPostSummaryMapper          same minus description (projection rule)

application.properties  job.company.resolve.enabled=true
                        job.company.resolve.every=15m
                        job.company.resolve.batch-size=200
```

Tests follow `CLAUDE.md`: unit under `unit_tests/` (`CompanySlugTest` from the D3 table,
`CompanyMapperTest`, `JobPostMapperTest` fallback, both DTO mappers for null-not-empty-string and
the projection rule), component under `component_tests/` with two seeded pull targets for one
employer across two source types. Hibernate drop-and-create builds `crawler.company` from the
entity in tests, so only `src/test/resources/db/test-seeds.sql` needs new rows. No WireMock:
job-service has no outbound HTTP for this feature.

## Blast radius of the shared contract change

Verified by a **full-reactor `mvn -DskipTests test-compile`** on this branch (green), not by a
scoped `-pl` build. This is the #330 check.

| Consumer | Uses | Impact |
|---|---|---|
| **api-contracts** | generates `job-api` models | Additive only: `CompanyInfo` gains 10 properties, no existing property changes name or type (`name: String`, `logoUrl: URI` untouched). `slug` carries `@Pattern`/`@Size`, which are inert on a response model (`returnResponse=true`, no `@Valid` on resource returns). |
| **job-service** | produces `CompanyInfo` | The real work (#435). `JobPostResponseMapperTest`/`JobPostSummaryMapperTest` and `JobResourceComponentTest` need new assertions. Existing `TC-1` uses `hasItems` on the key set, not exact equality, so additive fields do not break it. Note that `TC-2` only guards **top level** `description`, so nothing today would catch a company description leaking into the card list: that needs its own case. |
| **application-service** | hand-written `JobPostRemoteResponse.CompanyInfo(String name, String logoUrl)` record, **not** the generated model | Unaffected at compile time and at runtime: both the outer record and the nested one carry `@JsonIgnoreProperties(ignoreUnknown = true)`. The apply-time logo snapshot (`db/init/031`) is unchanged. Only effect: a slightly larger `GET /jobs/{id}` payload. |
| **notification-service** | generated `JobPostSummary` + `CompanyInfo` in `JobSearchGatewayAdapter` (`getCompany().getName()`, `getCompany().getLogoUrl()`) | Source compatible: additive properties do not touch those getters, and `logoUrl` stays `URI` (its `DigestJob.companyLogoUrl` is a `URI`). WireMock stubs that omit the new fields deserialise them as null. Verified: unit + main sources compile. |
| **auth-service / crawler-service** | depend on api-contracts but not on the job models | No impact (no `job.contract` import in either). |
| **JobHub-ui** | `src/api/mappers.js` | The behaviour change users see (#436). Three specific traps: (a) `jobFromApi` synthesises `industry: "-"`, `size: "-"`, `hq: location` and must stop; (b) it registers a company only `if (!companies[co.key])`, so a rich `GET /jobs/{id}` company arriving after a list entry is **dropped**: it must upgrade the entry, never downgrade it, which is exactly the projection rule in D4; (c) its local `slug()` lowercases, strips non-alphanumerics and **truncates at 16 characters**, so it is not the backend slug and will collide differently: use `dto.company.slug` when present and keep the local derivation only as the fallback. Also note the job-level `tags: []` in `jobFromApi` is a **different** field from `company.tags`. |
| **db / running stack** | `db/init` + both compose files | New file must be mounted in both compose files and hand-applied on existing volumes (#437). job-service runs Hibernate `validate` in prod, so the migration must land before the new entity mapping does. |

## Consequences

- **Positive.** Every acceptance criterion of #428 is satisfied structurally rather than by
  best effort: the link is total (NOT NULL `target_id`), so "every existing job post still resolves
  a company name" cannot regress; two sources for one employer collapse by slug; unknown fields are
  null by contract; the fallback makes the pre-resolution window invisible to users.
- **Positive.** No new query on the hot search path, no N+1, no change to the count/facet caches,
  no new DB role, no `.env` change, and no crawler change.
- **Cost.** job-service now writes into two more places in the crawler schema
  (`crawler.company`, and one column of `crawler.pull_target`). The ownership split inside the
  crawler schema, already true for three tables, grows to five objects and has to stay documented.
- **Cost.** The slug rule exists twice for one migration: canonical in Java, mirrored in SQL for the
  historical backfill. Contained by "the reconciler only touches NULL `company_id`", but it is real
  and must be called out in the migration's header comment.
- **Cost.** `CompanyInfo` now carries fields that nothing populates yet (`tags`, and in #428 also
  most of the rest). That is the point of a freeze, but it means the UI must be written to render
  sparse data from day one.
- **Follow-ups, explicitly out of scope here.**
  - The company **facet** and the company **filter** still group by `t.companyName`
    (`JobPostPanacheRepository:136`, `:461`, `:610`), so "Stripe" and "Stripe, Inc." remain two
    buckets. Regrouping on `company.slug` would change the facet values that
    `crawler.saved_filter` has already persisted inside serialised `FilterValues`, breaking users'
    saved presets. That needs its own story with a migration for saved filters.
  - Logo sourcing (#429), admin editing and company merge (#430), sparse-company alerting (#431),
    tag filtering/faceting.
  - The empty `job` schema and job-service's misleading `default-schema=job`.

## Alternatives considered

- **`job.company` with a cross-schema FK from `crawler.pull_target`.** Rejected: it inverts FK
  ownership (a crawler-owned table pointing at a job-owned one), complicates grants and the
  drop/recreate lifecycle, and buys no isolation that matters inside one database and one
  connection. The story's "job schema" wording describes ownership, which D1 preserves.
- **`job_post.company_id` as the link.** Rejected: it forces a backfill of the largest table, an
  `UPDATE` grant to job-service on the crawler's core write table, and, worst, leaves every newly
  crawled posting NULL, which would make a crawler change or a permanent per-post resolver
  mandatory. Kept as the documented forward path if an aggregator source ever appears.
- **Resolve the company in crawler-service at crawl time.** Rejected for this story: it widens the
  blast radius to a second service and a second schema owner for zero user-visible gain, since the
  target-level link already covers every current source. Revisit when a source starts returning
  per-posting employer data.
- **A separate slim `CompanySummary` schema for `JobPostSummary`.** Deferred: it would type-enforce
  the projection rule, but it doubles the frozen surface for one field. The nullable + documented
  projection rule achieves the same payload shape; introduce the second type only if a consumer
  needs to distinguish "not loaded" from "not set" for company `description`.
- **Enums for `industry`, `size`, and an exposed `source`.** Rejected: a frozen closed list on a
  response type breaks consumers on the first unknown value, and a single provenance scalar
  misrepresents what is genuinely per-field provenance.
- **Hardcoded company data, or a live third-party lookup on the read path.** Already rejected by
  story #428; recorded here so the ADR stands alone.
