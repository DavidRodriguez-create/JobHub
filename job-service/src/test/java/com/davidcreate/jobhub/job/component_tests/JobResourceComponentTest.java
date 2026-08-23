package com.davidcreate.jobhub.job.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Component tests for {@code JobResource}, exercising the contract defined in
 * {@code api-contracts/openapi/job-service.yaml}.
 *
 * <p>Backed by a real PostgreSQL instance auto-provisioned by Quarkus DevServices
 * (no WireMock here — job-service has no outbound HTTP calls). Fixtures come
 * from {@code src/test/resources/db/test-seeds.sql} (5 baseline rows).
 *
 * <p>Server-error (500) cases live in {@link JobResourceFailureComponentTest},
 * which uses {@code @InjectMock} on the repository — incompatible with the real
 * DevServices DB used here.
 */
@QuarkusTest
@DisplayName("Job Resource Component Tests")
class JobResourceComponentTest {

    private static final String JOBS = "/jobs";
    private static final UUID KNOWN_JOB_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Nested
    @DisplayName("GET /jobs")
    class SearchJobs {

        @Test
        @DisplayName("✓ no params → 200 OK & default page of jobs wrapped in JobSearchPage")
        void testSearchNoParams() {
            given()
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(14))
                    .body("page", equalTo(0))
                    .body("size", equalTo(20))
                    .body("totalPages", equalTo(1))
                    .body("content.size()", equalTo(14));
        }

        @Test
        @DisplayName("TC-1: content[] carries every JobPostSummary card field (Story #330)")
        void testSearchNoParamsCardFields() {
            given()
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content.size()", equalTo(14))
                    .body("content[0].keySet()", hasItems(
                            "id", "title", "url", "location", "locations",
                            "firstSeenAt", "lastSeenAt", "company", "compensationMin",
                            "compensationMax", "language", "employmentType", "careerLevel",
                            "source"));
        }

        @Test
        @DisplayName("TC-2 (load-bearing): content[] never carries description or requirements (Story #330)")
        void testSearchNoParamsExcludesHeavyFields() {
            given()
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content.collect { it.keySet() }.flatten().unique()", not(hasItem("description")))
                    .body("content.collect { it.keySet() }.flatten().unique()", not(hasItem("requirements")));
        }

        // ── QAE-428-C-02 ──────────────────────────────────────────────────────────
        @Test
        @DisplayName("QAE-428-C-02 (TC-2 hole-close): no entry in content[] ever carries a "
                + "non-null company.description, even though the Stripe company (row 1) now "
                + "has a populated description")
        void testSearchNoParamsExcludesCompanyDescription() {
            given()
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content.company.description.findAll { it != null }", empty());
        }

        @Test
        @DisplayName("✓ keyword provided → 200 OK & keyword-filtered jobs (TC-3: still excludes heavy fields)")
        void testSearchWithKeyword() {
            given()
                    .queryParam("keyword", "Java")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content.size()", greaterThanOrEqualTo(1))
                    .body("content.title", everyItem(matchesRegex("(?i).*java.*")))
                    .body("content[0].keySet()", not(hasItem("description")))
                    .body("content[0].keySet()", not(hasItem("requirements")));
        }

        @Test
        @DisplayName("✓ location='Madrid, Spain' → 200 OK & city+country filtered (primary or additional opening, Story #1)")
        void testSearchWithLocationCityCountry() {
            // Story #1 (ADR 0017 / AC-1-FILTER-1): matches on ANY opening, not just the
            // primary — row 9's ADDITIONAL opening is Madrid/Spain (its primary composed
            // string is "Barcelona, Spain"), so every result's primary `location` string is
            // no longer guaranteed to equal the filter value; assert the primary-match rows
            // (1, 3, 6) are present alongside row 9 instead.
            given()
                    .queryParam("location", "Madrid, Spain")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content.size()", greaterThanOrEqualTo(1))
                    .body("content.id", hasItems(
                            "11111111-1111-1111-1111-111111111111",
                            "33333333-3333-3333-3333-333333333333",
                            "66666666-6666-6666-6666-666666666666",
                            "99999999-9999-9999-9999-999999999999"));
        }

        @Test
        @DisplayName("✓ location='Spain' → 200 OK & country-only filtered")
        void testSearchWithLocationCountryOnly() {
            given()
                    .queryParam("location", "Spain")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content.size()", greaterThanOrEqualTo(1));
        }

        @Test
        @DisplayName("✓ employmentType=full-time → 200 OK & filtered jobs (TC-3: still excludes heavy fields)")
        void testSearchByEmploymentType() {
            given()
                    .queryParam("employmentType", "full-time")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content.employmentType", everyItem(equalTo("full-time")))
                    .body("content[0].keySet()", not(hasItem("description")))
                    .body("content[0].keySet()", not(hasItem("requirements")));
        }

        @Test
        @DisplayName("✓ compensationMin filters out lower-paying postings")
        void testSearchByCompensationMin() {
            given()
                    .queryParam("compensationMin", 70000)
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content.size()", greaterThanOrEqualTo(1));
        }

        @Test
        @DisplayName("✓ pagination provided → totalElements unchanged, content trimmed (TC-4: excludes heavy fields on every page)")
        void testSearchWithPagination() {
            given()
                    .queryParam("page", 0)
                    .queryParam("size", 3)
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(14))
                    .body("totalPages", equalTo(5))
                    .body("content.size()", equalTo(3))
                    .body("content[0].keySet()", not(hasItem("description")))
                    .body("content[0].keySet()", not(hasItem("requirements")));

            given()
                    .queryParam("page", 1)
                    .queryParam("size", 3)
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(14))
                    .body("content.size()", equalTo(3))
                    .body("content[0].keySet()", not(hasItem("description")))
                    .body("content[0].keySet()", not(hasItem("requirements")));
        }

        @Test
        @DisplayName("✓ sort=oldest → results sorted by firstSeenAt ASC (TC-3: still excludes heavy fields)")
        void testSearchSortOldest() {
            given()
                    .queryParam("sort", "oldest")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content[0].firstSeenAt", notNullValue())
                    .body("content[0].keySet()", not(hasItem("description")))
                    .body("content[0].keySet()", not(hasItem("requirements")));
        }

        @Test
        @DisplayName("✓ language=Spanish → 200 OK & only the Spanish-tagged role returns"
                + " (TC-331-17/20: exact path -> countIsEstimate absent/false, totalPages==1)")
        void testSearchByLanguageSingle() {
            given()
                    .queryParam("language", "Spanish")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(1))
                    .body("totalPages", equalTo(1))
                    .body("countIsEstimate", anyOf(nullValue(), is(false)))
                    .body("content[0].language", everyItem(notNullValue()));
        }

        @Test
        @DisplayName("✓ language=Spanish & language=German → union of both")
        void testSearchByLanguageMulti() {
            given()
                    .queryParam("language", "Spanish")
                    .queryParam("language", "German")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(2));
        }

        @Test
        @DisplayName("✓ language=Italian (no match) → 200 OK with empty content"
                + " (TC-331-19: zero matches never flagged as an estimate)")
        void testSearchByLanguageNoMatch() {
            given()
                    .queryParam("language", "Italian")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(0))
                    .body("countIsEstimate", anyOf(nullValue(), is(false)))
                    .body("content", empty());
        }

        @Test
        @DisplayName("AC-407-1 (#407 bug repro): language=English → 200 OK, not 500 "
                + "(array_overlaps(text[], varchar[]) type mismatch against the real text[] column)")
        void testSearchByLanguageEnglish() {
            var body = given()
                    .queryParam("language", "English")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(7))
                    .body("content.size()", equalTo(7))
                    .body("content.language", everyItem(notNullValue()))
                    .extract().jsonPath();

            assertThat(body.<String>getList("content.id")).containsExactlyInAnyOrder(
                    "11111111-1111-1111-1111-111111111111",
                    "22222222-2222-2222-2222-222222222222",
                    "33333333-3333-3333-3333-333333333333",
                    "44444444-4444-4444-4444-444444444444",
                    "55555555-5555-5555-5555-555555555555",
                    "66666666-6666-6666-6666-666666666666",
                    "77777777-7777-7777-7777-777777777777");
        }

        @Test
        @DisplayName("AC-407-3: language=English&language=Spanish → OR union across values, "
                + "no duplicates even though row 1 carries both")
        void testSearchByLanguageMultiUnionNoDuplicates() {
            var body = given()
                    .queryParam("language", "English")
                    .queryParam("language", "Spanish")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(7))
                    .extract().jsonPath();

            java.util.List<String> ids = body.getList("content.id");
            assertThat(ids).hasSize(7);
            assertThat(new java.util.HashSet<>(ids)).hasSize(7);
        }

        @Test
        @DisplayName("AC-407-4 (#407 bug ticket wording): language=Klingon → 200 OK empty content, not 500")
        void testSearchByLanguageKlingonNoMatch() {
            given()
                    .queryParam("language", "Klingon")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(0))
                    .body("countIsEstimate", anyOf(nullValue(), is(false)))
                    .body("content", empty());
        }

        @Test
        @DisplayName("AC-407-5: language=English&employmentType=full-time → both filters applied, "
                + "contract row (33333333…) excluded")
        void testSearchByLanguageWithEmploymentType() {
            var body = given()
                    .queryParam("language", "English")
                    .queryParam("employmentType", "full-time")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(6))
                    .body("content.employmentType", everyItem(equalTo("full-time")))
                    .body("content.language", everyItem(hasItem("English")))
                    .extract().jsonPath();

            assertThat(body.<String>getList("content.id"))
                    .doesNotContain("33333333-3333-3333-3333-333333333333");
        }

        @Test
        @DisplayName("AC-407-5b: language + employmentType + postedWithin + sort (full story query shape) → "
                + "only the recent row 66666666…")
        void testSearchByLanguageWithEmploymentTypePostedWithinAndSort() {
            given()
                    .queryParam("language", "English")
                    .queryParam("employmentType", "full-time")
                    .queryParam("postedWithin", "month")
                    .queryParam("sort", "oldest")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(1))
                    .body("content[0].id", equalTo("66666666-6666-6666-6666-666666666666"));
        }

        @Test
        @DisplayName("✓ keyword matches no records → 200 OK with empty content (no 404)"
                + " (TC-331-19: zero matches never flagged as an estimate)")
        void testSearchNoMatchReturnsEmpty() {
            given()
                    .queryParam("keyword", "ZZZZZZNOMATCH")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(0))
                    .body("countIsEstimate", anyOf(nullValue(), is(false)))
                    .body("content", empty());
        }

        @Test
        @DisplayName("TC-331-18 (AC-331-2): paging keyword=Developer at size=1 covers all 4 rows exactly once, "
                + "totalElements/countIsEstimate identical on every page")
        void testPagingSumsToExactTotalWithNoDuplicateOrOmission() {
            java.util.Set<String> expectedIds = java.util.Set.of(
                    "11111111-1111-1111-1111-111111111111",
                    "22222222-2222-2222-2222-222222222222",
                    "33333333-3333-3333-3333-333333333333",
                    "55555555-5555-5555-5555-555555555555");

            java.util.List<String> collected = new java.util.ArrayList<>();
            for (int page = 0; page < 4; page++) {
                var body = given()
                        .queryParam("keyword", "Developer")
                        .queryParam("page", page)
                        .queryParam("size", 1)
                        .when().get(JOBS)
                        .then()
                        .statusCode(200)
                        .body("totalElements", equalTo(4))
                        .body("countIsEstimate", anyOf(nullValue(), is(false)))
                        .body("content.size()", equalTo(1))
                        .extract().jsonPath();
                collected.add(body.getString("content[0].id"));
            }

            assertThat(collected).hasSize(4);
            assertThat(new java.util.HashSet<>(collected)).isEqualTo(expectedIds);
        }

        @Test
        @DisplayName("TC-331-33 (AC-331-19): countIsEstimate is additive, every required contract field is still present")
        void testCountIsEstimateIsAdditiveOnly() {
            given()
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("$", hasKey("content"))
                    .body("$", hasKey("page"))
                    .body("$", hasKey("size"))
                    .body("$", hasKey("totalElements"))
                    .body("$", hasKey("totalPages"));
        }

        @Test
        @DisplayName("✗ negative page index → 400 bad request")
        void testSearchInvalidPage() {
            given()
                    .queryParam("page", -1)
                    .when().get(JOBS)
                    .then()
                    .statusCode(400);
        }

        @Test
        @DisplayName("✗ page size less than 1 or excessively high → 400 bad request")
        void testSearchInvalidSize() {
            given()
                    .queryParam("size", 0)
                    .when().get(JOBS)
                    .then()
                    .statusCode(400);

            given()
                    .queryParam("size", 10_000)
                    .when().get(JOBS)
                    .then()
                    .statusCode(400);
        }

        @Test
        @DisplayName("✗ unknown sort value → 400 bad request")
        void testSearchInvalidSort() {
            given()
                    .queryParam("sort", "bogus")
                    .when().get(JOBS)
                    .then()
                    .statusCode(400);
        }
    }

    /**
     * Story #331 / ADR 0018: the short-TTL count cache, black-box. Default profile
     * already gives {@code cache.enabled=true}, {@code cache.ttl=PT30S}, so no new
     * {@code @TestProfile} is needed: these run against the same real DevServices
     * DB and 11-row seed as {@link SearchJobs}.
     */
    @Nested
    @DisplayName("GET /jobs, count cache (Story #331)")
    class CountCache {

        @Test
        @DisplayName("TC-331-28 (AC-331-10): identical filters, different page, back-to-back -> identical totals")
        void identicalFiltersDifferentPageYieldIdenticalTotals() {
            var first = given()
                    .queryParam("keyword", "Developer")
                    .queryParam("page", 0)
                    .when().get(JOBS)
                    .then().statusCode(200)
                    .extract().jsonPath();

            var second = given()
                    .queryParam("keyword", "Developer")
                    .queryParam("page", 1)
                    .when().get(JOBS)
                    .then().statusCode(200)
                    .extract().jsonPath();

            assertThat(second.getLong("totalElements")).isEqualTo(first.getLong("totalElements"));
            assertThat(second.getInt("totalPages")).isEqualTo(first.getInt("totalPages"));
            assertThat(second.getBoolean("countIsEstimate")).isEqualTo(first.getBoolean("countIsEstimate"));
        }

        @Test
        @DisplayName("TC-331-29 (AC-331-11): changing the filter (keyword) yields a freshly computed total, not carried over")
        void changingFilterYieldsFreshTotal() {
            given()
                    .queryParam("keyword", "Developer")
                    .when().get(JOBS)
                    .then().statusCode(200)
                    .body("totalElements", equalTo(4));

            given()
                    .queryParam("keyword", "fintech")
                    .when().get(JOBS)
                    .then().statusCode(200)
                    .body("totalElements", equalTo(1));
        }

        @Test
        @DisplayName("TC-331-30 (AC-331-12): page/size/sort changes alone never change the reported total")
        void pageSizeSortChangesDoNotChangeTotal() {
            var first = given()
                    .queryParam("keyword", "Developer")
                    .queryParam("page", 0)
                    .queryParam("size", 2)
                    .when().get(JOBS)
                    .then().statusCode(200)
                    .body("content.size()", equalTo(2))
                    .extract().jsonPath();

            var second = given()
                    .queryParam("keyword", "Developer")
                    .queryParam("page", 1)
                    .queryParam("size", 1)
                    .when().get(JOBS)
                    .then().statusCode(200)
                    .body("content.size()", equalTo(1))
                    .extract().jsonPath();

            var third = given()
                    .queryParam("keyword", "Developer")
                    .queryParam("sort", "oldest")
                    .when().get(JOBS)
                    .then().statusCode(200)
                    .extract().jsonPath();

            // The CACHED quantity (totalElements/countIsEstimate) is a property of the
            // filter set only, so it stays identical across all three requests even though
            // page/size/sort differ. totalPages is DERIVED per-request from that shared
            // totalElements and the REQUEST's own size (ceil(totalElements / size)): it is
            // therefore expected to differ here (size=2, size=1, size=default/20), never
            // literally equal across requests with different sizes; asserting it against
            // each request's own size is what proves it stays consistent with the shared total.
            assertThat(second.getLong("totalElements")).isEqualTo(first.getLong("totalElements"));
            assertThat(third.getLong("totalElements")).isEqualTo(first.getLong("totalElements"));
            assertThat(second.getBoolean("countIsEstimate")).isEqualTo(first.getBoolean("countIsEstimate"));
            assertThat(third.getBoolean("countIsEstimate")).isEqualTo(first.getBoolean("countIsEstimate"));

            long totalElements = first.getLong("totalElements");
            assertThat(first.getInt("totalPages")).isEqualTo((int) Math.ceil(totalElements / 2.0));
            assertThat(second.getInt("totalPages")).isEqualTo((int) Math.ceil(totalElements / 1.0));
            assertThat(third.getInt("totalPages")).isEqualTo((int) Math.ceil(totalElements / 20.0));
        }
    }

    /**
     * Story #52 (ADR 0005) — Tier 2: full-text-search keyword behavior, replacing
     * {@code LOWER(title/description) LIKE '%term%'} with
     * {@code search_vector @@ plainto_tsquery('english', :keyword)}.
     *
     * <p>Seed rows (titles / descriptions relevant to keyword matching):
     * <pre>
     *  11  "Senior Java Developer"   / "Backend role with Spring and Quarkus"
     *  22  "Java Backend Engineer"   / "Java developer for fintech"
     *  33  "Frontend Developer"      / "React + TypeScript role"
     *  44  "Python Data Engineer"    / "Data pipelines on AWS"
     *  55  "Java Cloud Developer"    / "Cloud-native services"
     *  66  "Java DevOps Engineer"    / "CI/CD and infrastructure automation"
     *  77  "Remote DevOps Engineer"  / "Remote infrastructure automation role"
     * </pre>
     */
    @Nested
    @DisplayName("GET /jobs?keyword= — Full-text search (Tier 2)")
    class KeywordSearchFts {

        @Test
        @DisplayName("T2-00: setup smoke — keyword=Senior matches row 11 (search_vector populated end-to-end)")
        void setupSmokeSearchVectorPopulated() {
            given()
                    .queryParam("keyword", "Senior")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", greaterThanOrEqualTo(1))
                    .body("content.id", hasItems("11111111-1111-1111-1111-111111111111"));
        }

        @Test
        @DisplayName("T2-01: full word match — keyword=Developer matches rows 11, 22, 33, 55 (totalElements==4)")
        void fullWordMatchInTitle() {
            // "Developer" is a complete word in: row 11 title ("Senior Java Developer"),
            // row 22 description ("Java developer for fintech"), row 33 title
            // ("Frontend Developer"), row 55 title ("Java Cloud Developer"). search_vector
            // combines title (weight A) and description (weight B), so row 22 matches too
            // even though "Developer" is not in its title.
            given()
                    .queryParam("keyword", "Developer")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(4))
                    .body("content.id", hasItems(
                            "11111111-1111-1111-1111-111111111111",
                            "22222222-2222-2222-2222-222222222222",
                            "33333333-3333-3333-3333-333333333333",
                            "55555555-5555-5555-5555-555555555555"));
        }

        @Test
        @DisplayName("T2-02: full word match in description — keyword=fintech matches only row 22")
        void fullWordMatchInDescription() {
            given()
                    .queryParam("keyword", "fintech")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(1))
                    .body("content[0].id", equalTo("22222222-2222-2222-2222-222222222222"));
        }

        @Test
        @DisplayName("T2-03: multi-word AND — keyword='Java Developer' matches rows 11, 22, 55 (totalElements==3)")
        void multiWordAndMatch() {
            // plainto_tsquery('english', 'Java Developer') == 'java' & 'developer'.
            // Row 11: title has "Java" and "Developer" → both stems present.
            // Row 22: title has "Java"; description "Java developer for fintech" has
            //   "developer" → combined search_vector has both stems.
            // Row 55: title "Java Cloud Developer" has both stems.
            // Row 33 ("Frontend Developer") lacks "java"; row 66 ("Java DevOps Engineer")
            // lacks "developer" anywhere in title/description.
            given()
                    .queryParam("keyword", "Java Developer")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(3))
                    .body("content.id", hasItems(
                            "11111111-1111-1111-1111-111111111111",
                            "22222222-2222-2222-2222-222222222222",
                            "55555555-5555-5555-5555-555555555555"));
        }

        @Test
        @DisplayName("T2-04: multi-word AND across title and description — keyword='Python pipelines' matches only row 44")
        void multiWordAndAcrossTitleAndDescription() {
            given()
                    .queryParam("keyword", "Python pipelines")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(1))
                    .body("content[0].id", equalTo("44444444-4444-4444-4444-444444444444"));
        }

        @Test
        @DisplayName("T2-05: empty keyword string → no filter applied, totalElements==14")
        void emptyKeywordNoFilter() {
            given()
                    .queryParam("keyword", "")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(14));
        }

        @Test
        @DisplayName("T2-06: omitted keyword param → no filter applied, totalElements==14")
        void omittedKeywordNoFilter() {
            given()
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(14));
        }

        @Test
        @DisplayName("T2-07: whitespace-only keyword → no filter applied, totalElements==14")
        void whitespaceKeywordNoFilter() {
            given()
                    .queryParam("keyword", "   ")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(14));
        }

        @Test
        @DisplayName("T2-08: no match → 200 with empty content (not 404/500)")
        void noMatchReturnsEmpty() {
            given()
                    .queryParam("keyword", "ZZZZZZNOMATCH")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(0))
                    .body("content", empty());
        }

        @Test
        @DisplayName("T2-09: stop-word-only keyword → plainto_tsquery('english','the') is empty, matches zero rows")
        void stopWordOnlyKeywordMatchesNothing() {
            // 'the' is an English stop word — to_tsvector/plainto_tsquery drop it entirely,
            // producing an empty tsquery. An empty tsquery on the right-hand side of @@
            // matches zero rows. This is a documented behavioral difference from the old
            // LIKE '%the%' implementation (which would have matched on substrings).
            given()
                    .queryParam("keyword", "the")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(0))
                    .body("content", empty());
        }

        @Test
        @DisplayName("T2-10: special characters in keyword → 200 (no 500), behaves like keyword=java")
        void specialCharactersHandledSafely() {
            given()
                    .queryParam("keyword", "java&&;--")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(4))
                    .body("content.id", hasItems(
                            "11111111-1111-1111-1111-111111111111",
                            "22222222-2222-2222-2222-222222222222",
                            "55555555-5555-5555-5555-555555555555",
                            "66666666-6666-6666-6666-666666666666"));
        }

        @Test
        @DisplayName("T2-11: case insensitivity — keyword=JAVA and keyword=java return identical results")
        void caseInsensitivityPreserved() {
            var upper = given()
                    .queryParam("keyword", "JAVA")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .extract().jsonPath();

            var lower = given()
                    .queryParam("keyword", "java")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .extract().jsonPath();

            assertThat(upper.getInt("totalElements")).isEqualTo(lower.getInt("totalElements"));
            assertThat(upper.<String>getList("content.id"))
                    .containsExactlyInAnyOrderElementsOf(lower.getList("content.id"));
        }

        @Test
        @DisplayName("T2-12: partial-word substring no longer matches (behavioral change) — keyword=jav matches nothing")
        void partialWordNoLongerMatches() {
            given()
                    .queryParam("keyword", "jav")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(0))
                    .body("content", empty());
        }

        @Test
        @DisplayName("T2-13: stemming — keyword=automating matches rows 66 and 77 via the 'automation' stem")
        void stemmingMatchesRelatedWordForms() {
            given()
                    .queryParam("keyword", "automating")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(2))
                    .body("content.id", hasItems(
                            "66666666-6666-6666-6666-666666666666",
                            "77777777-7777-7777-7777-777777777777"));
        }
    }

    @Nested
    @DisplayName("GET /jobs/facets")
    class JobFacets {

        private static final String FACETS = "/jobs/facets";

        @Test
        @DisplayName("✓ returns table-wide companies with counts (Stripe=7, Spotify=3)")
        void facetsCompanies() {
            given()
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    .body("companies.value", hasItems("Spotify", "Stripe"))
                    .body("companies.find { it.value == 'Stripe' }.count", equalTo(7))
                    .body("companies.find { it.value == 'Spotify' }.count", equalTo(3));
        }

        @Test
        @DisplayName("✓ returns table-wide locations (Spain=7, Germany=2, Netherlands=1, Remote=2) — Story #1")
        void facetsLocations() {
            given()
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    .body("locations.value", hasItems("Germany", "Spain", "Netherlands", "Remote"))
                    .body("locations.find { it.value == 'Spain' }.count", equalTo(7))
                    .body("locations.find { it.value == 'Germany' }.count", equalTo(2))
                    .body("locations.find { it.value == 'Netherlands' }.count", equalTo(1))
                    .body("locations.find { it.value == 'Remote' }.count", equalTo(2));
        }

        @Test
        @DisplayName("✓ returns table-wide languages from the array column (English=7, German=1, Spanish=1)")
        void facetsLanguages() {
            given()
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    .body("languages.value", hasItems("English", "German", "Spanish"))
                    .body("languages.find { it.value == 'English' }.count", equalTo(7))
                    .body("languages.find { it.value == 'Spanish' }.count", equalTo(1));
        }

        @Test
        @DisplayName("✓ returns employment types and the overall compensation range")
        void facetsEmploymentTypesAndCompRange() {
            given()
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    .body("employmentTypes.value", hasItems("full-time", "contract"))
                    .body("employmentTypes.find { it.value == 'full-time' }.count", equalTo(9))
                    .body("compensationMin", equalTo(60000))
                    .body("compensationMax", equalTo(110000));
        }
    }

    @Nested
    @DisplayName("GET /jobs/{id}")
    class GetJobById {

        @Test
        @DisplayName("✓ existing ID → 200 OK & full contract response shape (TC-7: still includes description + requirements)")
        void testGetJobSuccess() {
            given()
                    .pathParam("id", KNOWN_JOB_ID)
                    .when().get(JOBS + "/{id}")
                    .then()
                    .statusCode(200)
                    .body("id", equalTo(KNOWN_JOB_ID.toString()))
                    .body("title", notNullValue())
                    .body("url", notNullValue())
                    .body("location", equalTo("Madrid, Spain"))
                    .body("company.name", equalTo("Stripe"))
                    .body("source", equalTo("greenhouse"))
                    .body("employmentType", equalTo("full-time"))
                    .body("compensationMin", equalTo(70000))
                    .body("description", notNullValue())
                    .body("requirements", notNullValue())
                    // ── QAE-428-C-01: resolved company, every field from the seeded row ──
                    .body("company.id", notNullValue())
                    .body("company.slug", equalTo("stripe"))
                    .body("company.industry", equalTo("Fintech"))
                    .body("company.size", equalTo("5001-10000"))
                    .body("company.headquarters", equalTo("San Francisco, United States"))
                    .body("company.website", equalTo("https://stripe.com"))
                    .body("company.logoUrl", equalTo("https://example.com/logos/stripe.png"))
                    .body("company.manuallyEdited", equalTo(false))
                    .body("company.updatedAt", notNullValue());
        }

        // ── QAE-428-C-03: list/detail field parity except description ───────────────
        @Test
        @DisplayName("QAE-428-C-03: the SAME Stripe posting's company sub-object is identical "
                + "between GET /jobs (list entry) and GET /jobs/{id} (detail), except description "
                + "(null on the list, populated on the detail)")
        void testCompanyFieldParityBetweenListAndDetail() {
            var listEntry = given()
                    .queryParam("size", 100)
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .extract().jsonPath();
            var listCompany = listEntry.getMap(
                    "content.find { it.id == '" + KNOWN_JOB_ID + "' }.company");

            var detailCompany = given()
                    .pathParam("id", KNOWN_JOB_ID)
                    .when().get(JOBS + "/{id}")
                    .then()
                    .statusCode(200)
                    .extract().jsonPath().getMap("company");

            assertThat(listCompany.get("id")).isEqualTo(detailCompany.get("id"));
            assertThat(listCompany.get("slug")).isEqualTo(detailCompany.get("slug"));
            assertThat(listCompany.get("name")).isEqualTo(detailCompany.get("name"));
            assertThat(listCompany.get("logoUrl")).isEqualTo(detailCompany.get("logoUrl"));
            assertThat(listCompany.get("website")).isEqualTo(detailCompany.get("website"));
            assertThat(listCompany.get("industry")).isEqualTo(detailCompany.get("industry"));
            assertThat(listCompany.get("size")).isEqualTo(detailCompany.get("size"));
            assertThat(listCompany.get("headquarters")).isEqualTo(detailCompany.get("headquarters"));
            assertThat(listCompany.get("tags")).isEqualTo(detailCompany.get("tags"));
            assertThat(listCompany.get("manuallyEdited")).isEqualTo(detailCompany.get("manuallyEdited"));
            assertThat(listCompany.get("updatedAt")).isEqualTo(detailCompany.get("updatedAt"));

            assertThat(listCompany.get("description")).isNull();
            assertThat(detailCompany.get("description")).isNotNull();
        }

        @Test
        @DisplayName("✗ non-existing ID → 404 not found (via ExceptionMapper)")
        void testGetJobNotFound() {
            given()
                    .pathParam("id", UUID.randomUUID())
                    .when().get(JOBS + "/{id}")
                    .then()
                    .statusCode(404);
        }
    }

    @Nested
    @DisplayName("Authenticated endpoints reject anonymous callers")
    class RequiresAuth {

        @Test
        @DisplayName("GET /jobs/saved without a token → 401")
        void listSavedJobsUnauthenticated() {
            given().when().get(JOBS + "/saved").then().statusCode(401);
        }

        @Test
        @DisplayName("GET /jobs/filters/saved without a token → 401")
        void listSavedFiltersUnauthenticated() {
            given().when().get(JOBS + "/filters/saved").then().statusCode(401);
        }
    }
}
