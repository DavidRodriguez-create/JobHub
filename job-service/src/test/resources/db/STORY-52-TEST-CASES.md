# Story #52 — Test Case Spec: Job Post Fetching Performance (FTS keyword search)

**Sub-issue:** #65 (jobhub-quality-engineer) · **Depends on:** #64 (architect, design frozen —
`db/init/017-job-post-perf.sql` already exists)

**Scope:** Verify Tier 1 (indexes — transparent) and Tier 2 (FTS keyword search replacing
`LIKE`) per the frozen migration `db/init/017-job-post-perf.sql`:

```java
// appendKeyword() — new implementation
" AND sql('? @@ plainto_tsquery(''english'', ?)', j.searchVector, :keyword) = true"
params.put("keyword", query.getKeyword());
```

`search_vector` = `setweight(to_tsvector('english', title), 'A') || setweight(to_tsvector('english', description), 'B')`.

No API contract change, no new endpoints — `GET /jobs` and `GET /jobs/facets` keep their
existing request/response shapes.

---

## ⚠️ Pre-implementation setup note (blocks Tier 2 component tests)

Component tests run against **DevServices Postgres with Hibernate `drop-and-create`**
(`job-service/src/test/resources/db/init-test.sql` + `application.properties`). Hibernate
builds the schema from `@Entity` definitions, **not** from `db/init/017-job-post-perf.sql`.

For the FTS cases (T2-xx) to be implementable, the developer must, as part of the TDD cycle:

1. Add a `searchVector` field to `JobPostEntity` mapped to the `search_vector tsvector`
   column (read-side only is fine — `@Column(name = "search_vector", insertable = false,
   updatable = false)`, or excluded from Hibernate entirely if the JPQL `sql()` escape can
   reference the column without a mapped field — confirm against the architect's exact
   `appendKeyword()` snippet).
2. Extend `job-service/src/test/resources/db/init-test.sql` (or add a new test-only
   migration script run after Hibernate's `drop-and-create`) so that:
   - the `search_vector` column exists,
   - the `trg_job_post_search_vector` trigger + function exist and fire on INSERT,
   - the `idx_job_post_search_vector_gin` GIN index exists (optional for correctness, but
     keep dev/test parity with `017-job-post-perf.sql`).
3. Confirm `test-seeds.sql` rows get `search_vector` populated — either via the trigger
   firing on the seed `INSERT`s, or via a backfill `UPDATE` appended to the test init
   script (mirroring step 4 of `017-job-post-perf.sql`).

**T2-00** (below) is the smoke case that proves this setup is correct before any keyword
case can pass — if T2-00 fails, the gap is in test-DB setup, not in `appendKeyword()`.

---

## Layer summary

| Layer | Test class | Cases |
|---|---|---|
| Unit (Mockito, JPQL string) | `unit_tests/adapter/out/persistence/AppendKeywordTest` (new) | T1-U1..U6 |
| Component — regression | `component_tests/JobResourceComponentTest` (existing, amend) | T1-C1..C6 |
| Component — regression | `component_tests/JobFacetsDrillDownComponentTest` (existing, amend) | T1-C7..C9 |
| Component — FTS keyword | `component_tests/JobResourceComponentTest` → `SearchJobs` nested, or new `KeywordSearchFts` nested | T2-00, T2-01..T2-10 |

---

## Tier 1 — Indexes are transparent (regression)

Indexes don't change query results, only plans. These cases prove **existing behavior is
unchanged** after `017-job-post-perf.sql` is applied to the test DB. No new assertions
needed beyond "still passes" — listed here so the end-review can confirm none were
weakened/dropped.

### T1-C1 — Regression: `GET /jobs` no params returns full seed set
- **Layer:** component (existing test, `JobResourceComponentTest.SearchJobs.testSearchNoParams`)
- **Given** the 7-row seed dataset with Tier-1 indexes applied
- **When** `GET /jobs` with no query params
- **Then** `200 OK`, `totalElements == 7`, `content.size() == 7`
- **Data:** existing `test-seeds.sql`, no changes
- **Note:** unchanged — confirms indexes don't alter row counts/order.

### T1-C2 — Regression: location filter (`city, country`) still exact-matches via `LOWER()`
- **Layer:** component (existing `testSearchWithLocationCityCountry`, `testSearchWithLocationCountryOnly`)
- **Given** seed data
- **When** `GET /jobs?location=Madrid, Spain` and `GET /jobs?location=Spain`
- **Then** `200 OK`, results filtered as before (city+country exact match, country-only match)
- **Note:** the new `idx_job_post_lower_city` / `idx_job_post_lower_country` functional
  indexes serve the existing `LOWER(j.city) = :loc` / `LOWER(j.country) = :loc` predicates
  unchanged — same JPQL, same results.

### T1-C3 — Regression: language array filter via `array_overlaps`
- **Layer:** component (existing `testSearchByLanguageSingle`, `testSearchByLanguageMulti`, `testSearchByLanguageNoMatch`)
- **Given** seed data (English=7, Spanish=1, German=1)
- **When** `GET /jobs?language=Spanish`, `?language=Spanish&language=German`, `?language=Italian`
- **Then** `200 OK`; `totalElements` == 1, 2, 0 respectively (unchanged from pre-index baseline)
- **Note:** `idx_job_post_languages_gin` accelerates `array_overlaps(j.languages, :languages)`
  without changing results.

### T1-C4 — Regression: employment type, compensation, pagination, sort
- **Layer:** component (existing `testSearchByEmploymentType`, `testSearchByCompensationMin`,
  `testSearchWithPagination`, `testSearchSortOldest`)
- **Given** seed data
- **When** the existing query params are sent
- **Then** identical `200 OK` results to pre-migration baseline
- **Note:** `idx_job_post_comp_range` (composite on `compensation_min, compensation_max`)
  serves the existing `compensationMin`/`compensationMax` predicates — same JPQL.

### T1-C5 — Regression: `GET /jobs/{id}` and 404 path unaffected
- **Layer:** component (existing `GetJobById.testGetJobSuccess`, `testGetJobNotFound`)
- **Given** seed data
- **When** `GET /jobs/{known-id}` and `GET /jobs/{random-id}`
- **Then** `200`/full shape, and `404` respectively — unchanged
- **Note:** sanity check that the new `search_vector` column/trigger don't break entity
  loading or the `ExceptionMapper` path.

### T1-C6 — Regression: 4xx validation paths unaffected
- **Layer:** component (existing `testSearchInvalidPage`, `testSearchInvalidSize`, `testSearchInvalidSort`)
- **Given** seed data
- **When** `GET /jobs?page=-1`, `?size=0`, `?size=10000`, `?sort=bogus`
- **Then** `400` in all cases — unchanged
- **Note:** confirms Tier 1/2 changes touch only `appendKeyword()` + new indexes, not
  validation.

### T1-C7 — Regression: facets table-wide counts (BE-F05/F06 baseline)
- **Layer:** component (existing `JobFacetsDrillDownComponentTest.BEF05F06.noParamsEqualsTableWide`)
- **Given** seed data
- **When** `GET /jobs/facets` with no params
- **Then** all facet counts match the documented table-wide totals (Stripe=5, Spotify=2,
  Spain=5, Germany=1, Remote=1, English=7, Spanish=1, German=1, full-time=6, contract=1,
  comp 60000–110000) — unchanged
- **Note:** confirms the new `LOWER(company_name)` index on `pull_target` doesn't change
  the companies facet grouping/ordering.

### T1-C8 — Regression: BE-F10 keyword facet narrowing still works under FTS
- **Layer:** component (existing `JobFacetsDrillDownComponentTest.BEF10.keywordNarrowsAllGroups`)
- **Given** seed data, keyword=`python`
- **When** `GET /jobs/facets?keyword=python`
- **Then** same result as documented (only row 44 "Python Data Engineer" matches; companies
  → Spotify=1, locations → Germany, careerLevels → senior=1)
- **Note:** **`python` is a full word present in the title** — `plainto_tsquery('python')`
  matches it under FTS exactly as `LIKE '%python%'` did. This case is **regression-safe**
  and requires no behavioral re-spec. Flag for the developer: re-run as-is; if it fails,
  it's a real FTS gap, not an expected behavioral change.

### T1-C9 — Regression: BE-F12 keyword+location facet combo still works under FTS
- **Layer:** component (existing `JobFacetsDrillDownComponentTest.BEF12.*`)
- **Given** seed data, keyword=`java`
- **When** `GET /jobs/facets?keyword=java&location=Germany` and `?keyword=java&location=Spain`
- **Then** same results as documented (no-match for Germany; Spain narrows to rows
  11/22/55/66, Stripe=3/Spotify=1, Spain=4)
- **Note:** **`java` is a full word** present in all 4 matching titles
  ("Senior **Java** Developer", "**Java** Backend Engineer", "**Java** Cloud Developer",
  "**Java** DevOps Engineer"). `plainto_tsquery('java')` matches all four under FTS the
  same as `LIKE '%java%'` did. Regression-safe, no re-spec needed.

---

## Tier 2 — Full-text search keyword behavior

### T2-00 — Setup smoke test: `search_vector` is populated for seed rows
- **Layer:** component, new dedicated nested class (e.g. `KeywordSearchFts`), runs first
- **Given** the 7-row seed dataset after Hibernate `drop-and-create` + test init script
- **When** a keyword that is a complete word in row 1's title (`Senior Java Developer`),
  e.g. `keyword=Senior`, is sent to `GET /jobs`
- **Then** `200 OK`, `totalElements >= 1`, and row `11111111-1111-1111-1111-111111111111`
  is present in `content`
- **Purpose:** proves `search_vector` is non-null/populated and the GIN index +
  `@@ plainto_tsquery` predicate is wired correctly end-to-end **before** any of the
  nuanced FTS cases below are evaluated. If this fails, block on the pre-implementation
  setup note above, not on `appendKeyword()` logic.

---

### Happy path — full word match in title

#### T2-01 — Full word match in title (single word)
- **Layer:** component
- **Given** seed row 1 (`title = "Senior Java Developer"`, id `11111111-...`)
- **When** `GET /jobs?keyword=Developer`
- **Then** `200 OK`; `content` includes row `11111111-...` and every other row whose title
  contains "Developer" as a complete word (rows 1, 2 "Java Backend Engineer" — no; check
  seed: rows with "Developer" in title are row 1 "Senior Java Developer" and row 3
  "Frontend Developer" and row 5 "Java Cloud Developer" → 3 matches). `totalElements == 3`.
- **Data:** existing seed, no changes.

#### T2-02 — Full word match in description (single word)
- **Layer:** component
- **Given** seed row 2 (`description = "Java developer for fintech"`)
- **When** `GET /jobs?keyword=fintech`
- **Then** `200 OK`; `totalElements == 1`; `content[0].id == "22222222-..."`
- **Note:** "fintech" appears only in row 2's description, not its title — exercises the
  `setweight(..., 'B')` description half of `search_vector`.

#### T2-03 — Multi-word AND match (both words present, same row)
- **Layer:** component
- **Given** seed row 1 (`title = "Senior Java Developer"`, `description = "Backend role
  with Spring and Quarkus"`)
- **When** `GET /jobs?keyword=Java Developer`
- **Then** `200 OK`; `totalElements == 1`; `content[0].id == "11111111-..."`
- **Note:** `plainto_tsquery('english', 'Java Developer')` becomes `'java' & 'developer'`
  — only row 1 has **both** stems ("java" in title, "developer" in title). Rows 3/5 have
  "Developer"/"Java" individually but not in a row that has both... **verify against seed**:
  - Row 1: title "Senior Java Developer" → has both "java" and "developer" ✓
  - Row 3: title "Frontend Developer" → has "developer" but not "java" ✗
  - Row 5: title "Java Cloud Developer" → has both "java" and "developer" ✓
  - Row 6: title "Java DevOps Engineer" → has "java" but not "developer" ✗

  **Corrected expectation:** `totalElements == 2` (rows 1 and 5). Developer must verify
  the exact count against the live seed and update this case's expected count if the
  analysis above is off — the *intent* (AND semantics across title+description, case
  insensitive) is what's being tested, not a magic number. **Action item flagged to PDA/dev:
  confirm exact count before asserting.**

#### T2-04 — Multi-word AND across title and description
- **Layer:** component
- **Given** seed row 4 (`title = "Python Data Engineer"`, `description = "Data pipelines on
  AWS"`)
- **When** `GET /jobs?keyword=Python pipelines`
- **Then** `200 OK`; `totalElements == 1`; `content[0].id == "44444444-..."`
- **Note:** "Python" is in the title (weight A), "pipelines" is in the description
  (weight B). `plainto_tsquery` ANDs across the combined vector regardless of weight —
  this proves the title+description concatenation works for cross-field AND matches.

---

### Edge cases — empty / null / no-match / special characters / stop words

#### T2-05 — Empty keyword string → no keyword filter applied
- **Layer:** component
- **Given** seed data (7 rows)
- **When** `GET /jobs?keyword=` (empty string)
- **Then** `200 OK`; `totalElements == 7` (same as no keyword param at all)
- **Note:** mirrors the existing `appendKeyword()` guard
  (`query.getKeyword() != null && !query.getKeyword().isBlank()`) — an empty/blank string
  must short-circuit and NOT be passed to `plainto_tsquery` (which would otherwise produce
  an empty tsquery; behavior is technically "matches nothing" in Postgres for
  `plainto_tsquery('')`, so the guard is load-bearing under FTS too, not just LIKE).

#### T2-06 — Omitted keyword param → no keyword filter applied
- **Layer:** component
- **Given** seed data (7 rows)
- **When** `GET /jobs` (no `keyword` param at all)
- **Then** `200 OK`; `totalElements == 7`
- **Note:** regression of `testSearchNoParams`, restated here for traceability with the
  FTS cases — confirms the `null` branch of the guard is also unaffected.

#### T2-07 — Whitespace-only keyword → no keyword filter applied
- **Layer:** component
- **Given** seed data (7 rows)
- **When** `GET /jobs?keyword=%20%20%20` (URL-encoded spaces, i.e. `"   "`)
- **Then** `200 OK`; `totalElements == 7`
- **Note:** `.isBlank()` guard covers this — same as T2-05 but exercises the blank (not
  just empty) branch.

#### T2-08 — No match returns empty content with 200 (not 404/500)
- **Layer:** component
- **Given** seed data (7 rows)
- **When** `GET /jobs?keyword=ZZZZZZNOMATCH`
- **Then** `200 OK`; `totalElements == 0`; `content` empty
- **Note:** regression of existing `testSearchNoMatchReturnsEmpty` — restated for FTS:
  `plainto_tsquery('english', 'zzzzzznomatch')` produces a tsquery for an unrecognized
  token (treated as a literal lexeme after stemming) that matches nothing in any seed
  row's `search_vector`. Must still be `200` + empty, never `500`.

#### T2-09 — Stop-word-only keyword → matches nothing (or everything) — document actual behavior
- **Layer:** component
- **Given** seed data (7 rows)
- **When** `GET /jobs?keyword=the` (English stop word, removed by `to_tsvector('english',
  ...)` from both the document vectors and the query)
- **Then** `200 OK`. **Expected:** `plainto_tsquery('english', 'the')` returns an **empty
  tsquery** (all lexemes are stop words and get dropped). An empty tsquery on the
  right-hand side of `@@` matches **zero rows** in Postgres (empty tsquery has no lexemes
  to satisfy). So `totalElements == 0`, `content` empty.
- **Note:** **this is a documented behavioral difference from LIKE** — `LIKE '%the%'`
  would have matched any row containing the substring "the" (e.g. inside "Engineer",
  "Developer" — wait, those don't contain "the" as substring, but e.g. "fintech" does not
  either). Regardless of the exact LIKE-era count, the FTS behavior must be verified
  empirically against the live seed and asserted exactly — this case exists primarily to
  **pin down and document** the stop-word edge case so it doesn't surprise users searching
  for short common words. **Developer: run once, record the actual `totalElements`, encode
  that as the assertion** (do not guess).

#### T2-10 — Special characters in keyword are handled safely (no 500)
- **Layer:** component
- **Given** seed data (7 rows)
- **When** `GET /jobs?keyword=java%26%26%3B--` i.e. `keyword="java&&;--"` (SQL-meta and
  tsquery-operator characters: `&`, `;`, SQL comment `--`)
- **Then** `200 OK` (never `500`); `plainto_tsquery('english', 'java&&;--')` ignores
  punctuation/operators and effectively searches for the word "java" → same result set as
  `?keyword=java` (rows 1, 2, 5, 6 — all "Java*" titles)
- **Note:** this is the safety case for switching from a hand-built `LIKE '%...%'` pattern
  (where `%`/`_` are LIKE wildcards needing escaping — not currently escaped, pre-existing
  gap, out of scope here) to `plainto_tsquery`, which treats the input as plain text and
  strips special characters/operators. Also covers `params.put("keyword",
  query.getKeyword())` — the **raw, non-lowercased** keyword is now passed (lowercasing
  is no longer done in Java since `to_tsquery`/`plainto_tsquery` normalize case via the
  `english` config) — confirm mixed-case + special-char input doesn't bypass the
  parameter binding (i.e. it's bound via `:keyword`, never concatenated).

#### T2-11 — Case insensitivity preserved under FTS
- **Layer:** component
- **Given** seed row 1 (`title = "Senior Java Developer"`)
- **When** `GET /jobs?keyword=JAVA` (uppercase) and `GET /jobs?keyword=java` (lowercase)
- **Then** both return `200 OK` with **identical** `totalElements` and the same set of
  `content[].id` values
- **Note:** confirms `to_tsvector('english', ...)` / `plainto_tsquery('english', ...)`
  normalize case identically — this was true under `LOWER(...) LIKE LOWER(...)` and must
  remain true under FTS (regression of an implicit existing guarantee, now done by
  Postgres text-search normalization instead of `LOWER()` in Java).

---

### Documented behavioral change — partial-word search no longer matches

#### T2-12 — Partial-word substring no longer matches (BEHAVIORAL CHANGE, expected)
- **Layer:** component
- **Given** seed rows with titles containing the full word "Java" (rows 1, 2, 5, 6)
- **When** `GET /jobs?keyword=jav` (partial word — substring of "Java", not a complete
  English word/stem)
- **Then** `200 OK`; `totalElements == 0`; `content` empty
- **Note:** **THIS IS AN INTENTIONAL BEHAVIORAL CHANGE from the LIKE implementation.**
  Under the old `LOWER(j.title) LIKE '%jav%'`, all 4 "Java*" rows would have matched.
  Under `plainto_tsquery('english', 'jav')`, "jav" is not a recognized English word/stem
  and does not match the "java" lexeme in `search_vector`. This case must assert
  **zero results**, the opposite of pre-migration behavior. **If this case fails (i.e.
  still returns matches), the FTS migration has NOT taken effect** — likely because the
  test DB still has the old `appendKeyword()`/LIKE query or `search_vector` is stale.
  Flag prominently in PR description / changelog: "partial-word keyword search is no
  longer supported; users must search whole words (with English stemming, e.g.
  'running' still matches 'run')."

#### T2-13 — Stemming match (related word forms via English stemmer)
- **Layer:** component
- **Given** seed row 6 (`title = "Java DevOps Engineer"`, `description = "CI/CD and
  infrastructure automation"`); none of the seed descriptions literally contain the word
  "automating" or "automated", only "automation"
- **When** `GET /jobs?keyword=automating` (a different inflection of "automation")
- **Then** `200 OK`; `plainto_tsquery('english', 'automating')` stems to `automat`, which
  also matches the stem of "automation" (`automat`) in row 6's and row 7's descriptions
  (`"CI/CD and infrastructure automation"`, `"Remote infrastructure automation role"`) →
  `totalElements == 2` (rows 6 and 7)
- **Note:** **THIS IS A NEW CAPABILITY enabled by FTS, not possible under LIKE** (LIKE
  `'%automating%'` would have matched 0 rows since the literal substring "automating"
  doesn't exist in any seed row). Document as a positive behavioral change alongside
  T2-12's negative one — together they characterize the LIKE→FTS tradeoff for the PR
  description / release notes.

---

## Unit tests — `appendKeyword()` JPQL/param construction

These mirror the existing `AppendFiltersExceptTest` style (Mockito, no DB) and test the
**string + parameter-map construction**, not actual SQL execution. New class:
`unit_tests/adapter/out/persistence/AppendKeywordTest` (or add a `@Nested
AppendKeyword` class inside `AppendFiltersExceptTest` if preferred — but note the
existing class is keyed to `appendFiltersExcept`, so a sibling class is cleaner).

#### T1-U1 — Keyword present → JPQL contains the `sql(...) @@ plainto_tsquery(...)` fragment
- **Layer:** unit (Mockito/plain JUnit, no DB)
- **Given** a `JobSearchQuery` with `keyword = "java developer"`
- **When** `appendKeyword(jpql, params, query)` is called (or via `appendFilters`/
  `appendFiltersExcept` as currently exercised)
- **Then** the resulting JPQL string contains the `sql('? @@ plainto_tsquery(''english'',
  ?)', j.searchVector, :keyword) = true` fragment (exact literal per the architect's
  frozen snippet)
- **Data:** none (pure string assertion)

#### T1-U2 — Keyword present → `params` contains raw (non-lowercased) keyword bound to `:keyword`
- **Layer:** unit
- **Given** a `JobSearchQuery` with `keyword = "Java Developer"` (mixed case)
- **When** `appendKeyword(jpql, params, query)` is called
- **Then** `params.get("keyword")` equals the **raw** string `"Java Developer"` —
  **not** lowercased and **not** wrapped in `%...%` (both of those were artifacts of the
  LIKE implementation and must be removed)
- **Note:** this is the key regression-guard against accidentally leaving the old
  `"%" + keyword.toLowerCase() + "%"` wrapping in place after the JPQL string changes —
  a case that would compile and even partially "work" but defeat `plainto_tsquery`'s
  normalization.

#### T1-U3 — Null keyword → no clause appended, no param bound
- **Layer:** unit
- **Given** a `JobSearchQuery` with `keyword = null`
- **When** `appendKeyword(jpql, params, query)` is called
- **Then** `jpql` is unchanged (no `@@`/`plainto_tsquery`/`searchVector` substring added);
  `params` does not contain `"keyword"`
- **Note:** regression of the existing null-guard, now against the new clause text.

#### T1-U4 — Blank/whitespace keyword → no clause appended, no param bound
- **Layer:** unit
- **Given** a `JobSearchQuery` with `keyword = "   "`
- **When** `appendKeyword(jpql, params, query)` is called
- **Then** `jpql` unchanged; `params` does not contain `"keyword"`
- **Note:** regression of the existing `.isBlank()` guard.

#### T1-U5 — Empty string keyword → no clause appended, no param bound
- **Layer:** unit
- **Given** a `JobSearchQuery` with `keyword = ""`
- **When** `appendKeyword(jpql, params, query)` is called
- **Then** `jpql` unchanged; `params` does not contain `"keyword"`

#### T1-U6 — `appendFiltersExcept` still includes the keyword clause for every `Dimension`
- **Layer:** unit (extend existing `AppendFiltersExceptTest.KeywordAndPostedWithinAlwaysApply`)
- **Given** a fully-populated `JobSearchQuery` including `keyword = "java"`
- **When** `appendFiltersExcept(jpql, params, query, dim)` is called for each `Dimension`
- **Then** for every `dim`, the resulting JPQL contains the new FTS keyword fragment
  (`plainto_tsquery`/`searchVector`) and `params` contains `"keyword"` bound to `"java"`
  (not `"%java%"`)
- **Note:** this is a **textual update** to the existing
  `keywordAlwaysIncluded()` test — the assertion `.contains("keyword")` on the JPQL string
  still passes (the param name `:keyword` is unchanged), but the developer should also
  assert the param **value** is the raw keyword, not the LIKE-wrapped one, to catch the
  T1-U2 regression at this integration point too.

---

## Case → spec coverage matrix (for end-review)

| Case ID | Scenario | Layer | Maps to acceptance scenario |
|---|---|---|---|
| T1-C1..C9 | Existing search/facet regression | component | "all existing functionality still works" |
| T2-00 | search_vector populated (setup smoke) | component | prerequisite for Tier 2 |
| T2-01, T2-02 | Full word match (title, description) | component | "FTS keyword happy path" |
| T2-03, T2-04 | Multi-word AND | component | "FTS keyword happy path" |
| T2-05, T2-06, T2-07 | Empty/null/blank keyword → no filter | component | "FTS keyword edge cases" |
| T2-08 | No match → empty 200 | component | "FTS keyword edge cases" |
| T2-09 | Stop words only | component | "FTS keyword edge cases" |
| T2-10 | Special characters | component | "FTS keyword edge cases" |
| T2-11 | Case insensitivity | component | "FTS keyword edge cases" |
| T2-12 | Partial word no longer matches | component | "FTS behavioral change (documented)" |
| T2-13 | Stemming (new capability) | component | "FTS behavioral change (documented)" |
| T1-U1..U6 | appendKeyword JPQL/param construction | unit | underlying logic for all of the above |

---

## Open items for the developer / PDA

1. **T2-03's expected count (2 vs other)** must be verified against the live seed once FTS
   is wired up — don't hardcode without running it.
2. **T2-09's stop-word count** must be empirically determined and then pinned — don't
   guess.
3. **Pre-implementation setup note** (top of doc): `search_vector` column + trigger + GIN
   index must exist in the **test** DB (via `init-test.sql` or entity mapping), separate
   from the prod migration `017-job-post-perf.sql`. This is infrastructure work that
   blocks all T2-xx cases — recommend doing it first and using T2-00 as the gate.
4. If `JobPostEntity` needs a `searchVector` field for the JPQL `sql('?...', j.searchVector,
   ...)` escape to resolve `j.searchVector` to the column, confirm the exact mapping
   (read-only `tsvector` columns are sometimes mapped as `Object`/`String` with a custom
   `JdbcTypeCode` — architect should confirm if not already specified in #64's output).
