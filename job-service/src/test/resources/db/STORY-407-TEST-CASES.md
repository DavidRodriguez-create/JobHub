# Story #407 — Test Case Spec: `language` filter 500 (BUG regression)

**Sub-issue:** #415 (jobhub-quality-engineer) · **Bug, not a feature** — root cause in
`job-service/src/main/java/com/davidcreate/jobhub/job/adapter/out/persistence/JobPostPanacheRepository.java`,
method `appendLanguage` (~line 501):

```java
jpql.append(" AND array_overlaps(j.languages, :languages) = true");
params.put("languages", query.getLanguages().toArray(new String[0]));
```

`crawler.job_post.languages` is `TEXT[]` in prod (`db/init/010-crawler.sql:122`). The bound
parameter is a Java `String[]`, which JDBC/Hibernate sends to Postgres as
`character varying[]`. Postgres then evaluates `text[] && character varying[]`, which has
**no matching operator overload**, and Postgres raises `operator does not exist`, which
Quarkus surfaces as **HTTP 500** on `GET /jobs?language=…` (`search()`/`count()`) and on
**every** `GET /jobs/facets` call that has an active `language=` filter (`appendLanguage` is
re-invoked once per non-LANGUAGE facet dimension inside `appendFiltersExcept`).

`languageFacets()` (the native-SQL `unnest(jp.languages)` path that produces the `languages`
facet bucket itself) does **not** call `appendLanguage` — `Dimension.LANGUAGE` is always
excluded from its own group — so that bucket's *values* are unaffected by this bug. It is
still exercised indirectly whenever a `language=` param is present, because
`appendFiltersExcept` is called once per group including the language group's own call
(which excludes only the language clause, not the others) — see AC-407-6.

---

## PRECONDITION — blocks every case below (read first)

**Prod:** `crawler.job_post.languages` is `TEXT[]` (`db/init/010-crawler.sql:122`), and
`job-service` runs Hibernate in `validate` mode against it (never generates schema in prod).

**Test DB today:** `job-service` component tests run against DevServices Postgres built by
Hibernate `drop-and-create` from `@Entity` definitions
(`job-service/src/test/resources/application.properties` +
`src/test/resources/db/init-test.sql`). `JobPostEntity.languages` is mapped as:

```java
@Column(name = "languages")
@JdbcTypeCode(SqlTypes.ARRAY)
public List<String> languages;
```

with **no explicit `columnDefinition`**. Hibernate 6's default array-column DDL inference for
`List<String>` produces `character varying[]` (`varchar[]`), **not** `text[]`. So today's test
DB has `varchar[] && varchar[]` — no type mismatch — and every existing language-filter test
(`testSearchByLanguageSingle/Multi/NoMatch`, `BEF19.languageOwnDimensionExcluded` /
`languageSpanishOwnDimensionExcluded`, `JobResourceCountEstimateModeComponentTest`'s two
`language=Spanish` assertions) **passes today despite the prod bug being live** — they are
**vacuous** for this regression.

**Developer action required before any case below can fail-then-pass correctly:** make the
test DB's `crawler.job_post.languages` column `text[]`, matching prod exactly. Either:
- add `columnDefinition = "text[]"` to `JobPostEntity.languages` (forces Hibernate DDL and,
  more importantly, forces the JDBC array-binding type for `:languages` too — this is likely
  also part of the actual **fix**, not just test setup), or
- add a test-only DDL fixup in `init-test.sql`/`test-seeds.sql` that `ALTER`s the column to
  `text[]` after Hibernate's `drop-and-create` and before the seed `INSERT`s run.

**If the column stays `varchar[]` in the test DB, every case in this document is vacuous** —
flag this explicitly in the PR description and at end-review. This mirrors the exact gap this
story exists to close.

---

## Layer summary

| Layer | Test class | Cases |
|---|---|---|
| Component — bug repro (list) | `component_tests/JobResourceComponentTest` (existing `SearchJobs`, amend) | AC-407-1, AC-407-3, AC-407-4 |
| Component — bug repro (facets) | `component_tests/JobFacetsDrillDownComponentTest` (existing `BEF19`, amend) | AC-407-2 |
| Component — combined query shape | `component_tests/JobResourceComponentTest` (`SearchJobs`, new cases) | AC-407-5 |
| Component — regression | `JobResourceComponentTest`, `JobFacetsDrillDownComponentTest`, `JobResourceCountEstimateModeComponentTest` (existing, re-run only) | AC-407-6 |
| Unit — JPQL/param shape | `unit_tests/adapter/out/persistence/AppendFiltersExceptTest` (existing, amend) | AC-407-U1 |

---

## Component cases — bug reproduction (today: 500)

### AC-407-1 — `GET /jobs?language=English` returns only English-tagged jobs, not 500
- **Layer:** component (`JobResourceComponentTest.SearchJobs`)
- **Given** the 11-row seed (`test-seeds.sql`), with the PRECONDITION column-type fix applied;
  English is carried by rows 1–7 (`11111111…` … `77777777…`); rows 8–11 have empty language
  arrays
- **When** `GET /jobs?language=English`
- **Then** `200 OK` (today: `500`); `X-Total-Count` / `totalElements == 7`; `content.size() == 7`
  (bounded by default page size 20); every returned row's `language` field is non-null and every
  row ID is one of `11111111…`..`77777777…`
- **Data:** existing seed, no changes. **New test method** — `testSearchByLanguageSingle`
  already exists but uses `language=Spanish`; this is a distinct case using the exact value
  from the bug report (`English`), and is the one that must literally match the production
  repro steps.
- **Note:** once the PRECONDITION is applied, the *existing* `testSearchByLanguageSingle`
  (Spanish, expect 1), `testSearchByLanguageMulti` (Spanish+German, expect 2), and
  `testSearchByLanguageNoMatch` (Italian, expect 0) will **also** start failing with 500 until
  the fix lands — that is correct and expected; the fix must make all of them green, not just
  the new English case (see AC-407-6).

### AC-407-2 — `GET /jobs/facets?language=English` returns 200 with correct facet counts, not 500
- **Layer:** component (`JobFacetsDrillDownComponentTest.BEF19`, extend the existing
  `languageOwnDimensionExcluded` test — do not duplicate the class)
- **Given** the 11-row seed, PRECONDITION applied
- **When** `GET /jobs/facets?language=English`
- **Then** `200 OK` (today: `500`); the response shape is unchanged from the currently-written
  (but vacuous) assertions in `BEF19.languageOwnDimensionExcluded`:
  - `languages` group unaffected by its own filter (own-dimension exclusion): `English=7`,
    `Spanish=1`, `German=1`
  - `companies` narrowed to English-tagged rows only: `Stripe=5` (rows 1,2,3,6,7),
    `Spotify=2` (rows 4,5)
  - `locations` narrowed the same way: `Spain=5`, `Germany=1`, `Remote=1`
- **Data:** existing seed, no changes — this is the **existing** `BEF19` test class; it already
  encodes the correct expected values, it just never reproduces the bug today (PRECONDITION
  gap). No new assertions needed, just confirm it goes 500→200 once the column type + fix land.
- **Note:** this is the facets half of the bug report's exact repro (`GET /jobs/facets?language=English…`).

### AC-407-3 — Multi-value `language=English&language=Spanish` → OR union, no duplicates
- **Layer:** component (`JobResourceComponentTest.SearchJobs`, new case alongside
  `testSearchByLanguageMulti`)
- **Given** the 11-row seed, PRECONDITION applied; row 1 (`11111111…`) carries **both**
  English and Spanish
- **When** `GET /jobs?language=English&language=Spanish`
- **Then** `200 OK`; `totalElements == 7` — the union of English (rows 1–7) and Spanish
  (row 1 only) is still 7 distinct postings, **not 8** (row 1 must not be double-counted); each
  `content[].id` appears exactly once
- **Data:** existing seed, no changes.
- **Note:** this is the array-overlap "OR within the array, union across selected values, no
  duplicate rows" semantics that `array_overlaps(...) = ANY-match` is supposed to guarantee —
  worth asserting distinctness explicitly (e.g. `content.id.unique()` or count distinct IDs)
  since `existing testSearchByLanguageMulti` (Spanish+German, no overlap row) can't catch a
  duplication regression the way an overlapping-row case like this can.

### AC-407-4 — `language=Klingon` (no match, not a seeded value) → 200 empty, not 500
- **Layer:** component (`JobResourceComponentTest.SearchJobs`, new case; existing
  `testSearchByLanguageNoMatch` already covers this shape with `Italian` — this case exists to
  pin the exact wording from the bug ticket)
- **Given** the 11-row seed, PRECONDITION applied; no seed row carries `"Klingon"`
- **When** `GET /jobs?language=Klingon`
- **Then** `200 OK` (today: `500`); `totalElements == 0`; `content` empty; `countIsEstimate`
  absent or `false` (zero matches are never flagged as an estimate, per TC-331-19 convention
  already used by `testSearchByLanguageNoMatch`)
- **Data:** existing seed, no changes.
- **Note:** functionally identical to the existing `testSearchByLanguageNoMatch` (Italian) —
  keep both if the developer wants literal traceability to the bug ticket's example value,
  or fold this into that existing test as a second no-match value; either is acceptable, do
  not skip the assertion.

---

## Component case — the exact story query shape

### AC-407-5 — `language` combined with `employmentType` (+ `postedWithin` + `sort`) → 200, both/all filters applied
- **Layer:** component (`JobResourceComponentTest.SearchJobs`, new case — no existing test
  combines `language` with any other filter dimension)
- **Given** the 11-row seed, PRECONDITION applied. Among the English-tagged rows (1–7),
  `employmentType=full-time` is rows 1,2,4,5,6,7 (row 3 is `contract`)
- **When** `GET /jobs?language=English&employmentType=full-time`
- **Then** `200 OK`; `totalElements == 6`; every `content[]` row has `language` including
  `English` and `employmentType == "full-time"`; row `33333333…` (contract) is absent
- **Data:** existing seed, no changes.

#### AC-407-5b — same combo, adding `postedWithin` + `sort` (full story query shape)
- **Layer:** component, same test class, second assertion or a second `@Test`
- **Given** the same seed; row 6 (`66666666…`) is the only row seeded with
  `first_seen_at = NOW()` (the "recent" row per the seed comment); it is English + full-time
- **When** `GET /jobs?language=English&employmentType=full-time&postedWithin=month&sort=oldest`
- **Then** `200 OK`; `totalElements == 1`; `content[0].id == "66666666-6666-6666-6666-666666666666"`
- **Note:** `postedWithin=month` (30-day window) only keeps row 6 among the English+full-time
  set because every other seed row's `first_seen_at` is a fixed 2024 date, outside any
  reasonable rolling window from "now"; `sort=oldest` is a no-op assertion here (single result)
  but confirms `sort` + `language` don't conflict in the JPQL builder. This is the most
  representative reproduction of the production bug report's actual query shape
  (`language=English…` with additional params, not `language` alone).

---

## Component cases — regression (existing tests, re-run only, no new assertions)

### AC-407-6 — Non-language filters + the `languages` facet bucket itself stay green and unchanged
- **Layer:** component (existing tests, run as-is post-fix)
- **Given** the fix is applied (real `array_overlaps`/type-matching resolved) and the
  PRECONDITION column-type change is in the test DB
- **When** the full existing suite runs
- **Then** all of the following remain green with their **already-documented** expected values
  (no case may be weakened or have its assertion loosened to make it pass):
  - `JobResourceComponentTest.SearchJobs`: `testSearchNoParams` (totalElements==11),
    `testSearchWithLocationCityCountry`, `testSearchWithLocationCountryOnly`,
    `testSearchByEmploymentType`, `testSearchByCompensationMin`, `testSearchWithPagination`,
    `testSearchSortOldest`, `testSearchInvalidPage/Size/Sort` (400s)
  - `JobResourceComponentTest.JobFacets.facetsCompanies/facetsLocations/facetsLanguages`
    (`facetsLanguages` — the plain `GET /jobs/facets` **without** any `language=` query param
    — stays green with `English=7, Spanish=1, German=1`; this path never calls
    `appendLanguage` at all, so it is unaffected by the bug and must not regress)
  - `JobFacetsDrillDownComponentTest`: every `BE-F01`..`BE-F14` group that does **not** pass
    `language=`, plus the two `BEF19` cases now actually exercising the fix (see AC-407-2)
  - `JobResourceCountEstimateModeComponentTest.totalsNeverNegativeAcrossFilterCombinations`
    and `narrowFilterStillReturnsEstimateWhenModeForcesIt` (both use `language=Spanish`) — these
    were **also vacuous today** for the same PRECONDITION reason as AC-407-1's note; they must
    go 500→200 alongside the rest, not be skipped
- **Note to developer:** do **not** special-case `language=English` in the fix while leaving
  the general `appendLanguage`/`array_overlaps` binding broken for other values — every case
  above using any language value must pass, since the root cause is value-independent
  (array element type mismatch, not a specific string).

---

## Unit case — JPQL/param construction (regression only, no new behavior to unit test)

### AC-407-U1 — `appendLanguage` / `appendFiltersExcept(..., Dimension.LANGUAGE)` still compose the correct clause and raw param
- **Layer:** unit (Mockito, no DB) — extend existing
  `AppendFiltersExceptTest.ExcludeLanguage.omitsLanguageClause` and add a sibling assertion in
  a class that does **not** exclude `LANGUAGE`
- **Given** a `JobSearchQuery` with `languages = List.of("English", "Spanish")`
- **When** `appendFiltersExcept(jpql, params, query, Dimension.COMPANY)` (any dimension other
  than `LANGUAGE`, so the language clause is included) is called
- **Then** the JPQL contains the language clause (`array_overlaps(j.languages, :languages)` or
  whatever exact fragment the fix lands on) and `params.get("languages")` contains
  `["English", "Spanish"]`
- **Note:** this case is **not** where the bug reproduces — Mockito's mocked `EntityManager`
  never touches real Postgres, so the `text[]`/`varchar[]` type mismatch is invisible at this
  layer. This case only guards the JPQL string/param-map construction is unchanged by whatever
  code change fixes the binding (e.g. if the fix changes `:languages` to bind a `List<String>`
  instead of `String[].toArray()`, this assertion should be updated to match — flag as an
  **open item**: developer must confirm the exact param type post-fix and this test's exact
  assertion is not prescribed here on purpose, per "cases not implementations").
- **Data:** none (pure string/map assertion).

---

## Case → spec coverage matrix (for end-review)

| Case ID | Scenario | Layer | Today (before fix) | Maps to acceptance scenario |
|---|---|---|---|---|
| AC-407-1 | `GET /jobs?language=English` | component | 500 (once PRECONDITION applied) | bug repro — list |
| AC-407-2 | `GET /jobs/facets?language=English` | component | 500 (once PRECONDITION applied) | bug repro — facets |
| AC-407-3 | multi-value language OR, no dupes | component | 500 (once PRECONDITION applied) | bug repro — array union semantics |
| AC-407-4 | `language=Klingon` no match | component | 500 (once PRECONDITION applied) | bug repro — no-match must stay 200 |
| AC-407-5 / 5b | language + employmentType (+postedWithin+sort) | component | 500 (once PRECONDITION applied) | exact story query shape |
| AC-407-6 | non-language filters + `languages` bucket unaffected | component | green (unaffected code path) / vacuous-green (affected path) | regression, no weakening |
| AC-407-U1 | JPQL/param construction | unit | green (unaffected — DB-only bug) | underlying construction, not the repro itself |

---

## Open items for the developer / PDA

1. **PRECONDITION is the actual blocker.** Until `crawler.job_post.languages` is `text[]` in
   the test DB, every case above passes today (vacuously, pre-fix) and will only start
   reproducing the 500 once that column-type gap is closed. Do the column-type alignment
   *first*, confirm AC-407-1 goes 500 on the unfixed `appendLanguage`, *then* implement the fix.
2. **Existing "vacuous" tests must be identified and re-verified, not just left alone:**
   `testSearchByLanguageSingle/Multi/NoMatch`, `JobFacetsDrillDownComponentTest.BEF19.*`, and
   `JobResourceCountEstimateModeComponentTest`'s two `language=Spanish` assertions all currently
   pass for the wrong reason. At end-review, confirm they still assert their documented values
   post-fix (not weakened) and now actually exercise real Postgres type-checking.
3. **AC-407-U1's exact assertion is intentionally not prescribed** — it depends on the shape of
   the fix (e.g. cast in JPQL vs. `columnDefinition` vs. explicit param type). Developer should
   land the fix, then write this unit case to match the resulting `appendLanguage` output,
   without reintroducing the LIKE-era style of over-specifying implementation.
