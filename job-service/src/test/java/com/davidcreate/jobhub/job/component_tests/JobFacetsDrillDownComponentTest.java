package com.davidcreate.jobhub.job.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Component tests for the drill-down (filter-aware) facets endpoint
 * {@code GET /jobs/facets} — BE-F01 through BE-F14.
 *
 * <p>Seed (7 rows from test-seeds.sql):
 * <pre>
 *  id  company  city       country  type       career   comp-min  comp-max  languages          first_seen_at
 *  11  Stripe   Madrid     Spain    full-time  senior   70000     90000     English, Spanish   2024-01-01
 *  22  Stripe   Barcelona  Spain    full-time  mid      60000     80000     English            2024-02-01
 *  33  Stripe   Madrid     Spain    contract   junior   NULL      NULL      English            2024-03-01  ← unpriced
 *  44  Spotify  Berlin     Germany  full-time  senior   65000     85000     English, German    2024-04-01
 *  55  Spotify  Madrid     Spain    full-time  lead     80000     100000    English            2024-05-01
 *  66  Stripe   Madrid     Spain    full-time  senior   90000     110000    English            NOW()       ← recent
 *  77  Stripe   NULL       Remote   full-time  junior   NULL      NULL      English            2024-06-01  ← BE-F18: Remote
 * </pre>
 *
 * <p>Table-wide totals:
 * companies: Stripe=5, Spotify=2 | locations: Spain=5, Germany=1, Remote=1 (synthetic)
 * languages: English=7, Spanish=1, German=1 | employment: full-time=6, contract=1
 * careerLevels: junior=2, mid=1, senior=3, lead=1 | comp: 60000–110000
 */
@QuarkusTest
@DisplayName("GET /jobs/facets — Drill-down (BE-F01..F14)")
class JobFacetsDrillDownComponentTest {

    private static final String FACETS = "/jobs/facets";

    // ── BE-F01 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BE-F01: location filter narrows other groups; locations group stays full")
    class BEF01 {

        @Test
        @DisplayName("location=Germany → companies shows only Spotify; locations still shows Spain")
        void locationFilterNarrowsOtherGroupsButNotLocations() {
            given()
                    .queryParam("location", "Germany")
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    // companies group is filtered by location=Germany → rows 4 and 10 (Spotify, Story #1)
                    .body("companies.value", hasItem("Spotify"))
                    .body("companies.value", not(hasItem("Stripe")))
                    .body("companies.find { it.value == 'Spotify' }.count", equalTo(2))
                    // locations group excludes the location filter → still shows table-wide counts
                    .body("locations.value", hasItems("Spain", "Germany"))
                    .body("locations.find { it.value == 'Spain' }.count", equalTo(7))
                    .body("locations.find { it.value == 'Germany' }.count", equalTo(2));
        }
    }

    // ── BE-F02 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BE-F02: own-dimension independence")
    class BEF02 {

        @Test
        @DisplayName("company=Spotify → companies group still shows Stripe (not just Spotify)")
        void companyFilterOwnDimensionNotExcluded() {
            given()
                    .queryParam("company", "Spotify")
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    // companies group excludes company filter → still shows both companies
                    .body("companies.value", hasItems("Stripe", "Spotify"))
                    .body("companies.find { it.value == 'Stripe' }.count", equalTo(7))
                    .body("companies.find { it.value == 'Spotify' }.count", equalTo(3))
                    // locations is narrowed by company=Spotify → rows 4 (Germany), 5 (Spain),
                    // 10 (Germany primary + Remote additional, Story #1)
                    .body("locations.value", hasItems("Spain", "Germany", "Remote"))
                    .body("locations.find { it.value == 'Spain' }.count", equalTo(1))
                    .body("locations.find { it.value == 'Germany' }.count", equalTo(2))
                    .body("locations.find { it.value == 'Remote' }.count", equalTo(1));
        }

        @Test
        @DisplayName("employmentType=contract → employmentType group still shows all types")
        void employmentTypeFilterOwnDimensionStaysFullForItsGroup() {
            given()
                    .queryParam("employmentType", "contract")
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    // employmentTypes group excludes its own filter → still shows all employment types
                    .body("employmentTypes.value", hasItems("full-time", "contract"))
                    .body("employmentTypes.find { it.value == 'full-time' }.count", equalTo(9))
                    .body("employmentTypes.find { it.value == 'contract' }.count", equalTo(1));
        }
    }

    // ── BE-F03 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BE-F03: multi-select union within a dimension")
    class BEF03 {

        @Test
        @DisplayName("location=Spain + location=Germany → locations group shows union (9 posts in scope)")
        void multiSelectLocationUnion() {
            given()
                    .queryParam("location", "Spain")
                    .queryParam("location", "Germany")
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    // locations group excludes location filter → table-wide counts
                    .body("locations.find { it.value == 'Spain' }.count", equalTo(7))
                    .body("locations.find { it.value == 'Germany' }.count", equalTo(2))
                    // companies group is narrowed by Spain OR Germany (any opening, Story #1):
                    // Spain matches rows 1,2,3,5,6,8,9; Germany matches rows 4,10 → union of 9 posts
                    // Stripe: 1,2,3,6,8,9 = 6; Spotify: 4,5,10 = 3
                    .body("companies.find { it.value == 'Stripe' }.count", equalTo(6))
                    .body("companies.find { it.value == 'Spotify' }.count", equalTo(3));
        }
    }

    // ── BE-F04 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BE-F04: multi-dimension — each group excludes only its own")
    class BEF04 {

        @Test
        @DisplayName("location=Germany + company=Spotify → companies still shows all; locations still shows all")
        void multiDimensionEachGroupExcludesOnlyItsOwn() {
            given()
                    .queryParam("location", "Germany")
                    .queryParam("company", "Spotify")
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    // companies group: excludes company filter; applies location=Germany
                    // → Germany posts = rows 4, 10 (Story #1) → only Spotify
                    .body("companies.value", hasItem("Spotify"))
                    .body("companies.value", not(hasItem("Stripe")))
                    // locations group: excludes location filter; applies company=Spotify
                    // → Spotify posts = row 4 (Germany), row 5 (Spain), row 10 (Germany + Remote)
                    .body("locations.find { it.value == 'Germany' }.count", equalTo(2))
                    .body("locations.find { it.value == 'Spain' }.count", equalTo(1))
                    .body("locations.find { it.value == 'Remote' }.count", equalTo(1));
        }
    }

    // ── BE-F05 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BE-F05/F06: no-params == table-wide invariant")
    class BEF05F06 {

        @Test
        @DisplayName("no params → same as table-wide (Stripe=7, Spain=7, English=7, senior=3, Remote=2)")
        void noParamsEqualsTableWide() {
            given()
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    .body("companies.find { it.value == 'Stripe' }.count", equalTo(7))
                    .body("companies.find { it.value == 'Spotify' }.count", equalTo(3))
                    .body("locations.find { it.value == 'Spain' }.count", equalTo(7))
                    .body("locations.find { it.value == 'Germany' }.count", equalTo(2))
                    .body("locations.find { it.value == 'Netherlands' }.count", equalTo(1))
                    .body("locations.find { it.value == 'Remote' }.count", equalTo(2))
                    .body("languages.find { it.value == 'English' }.count", equalTo(7))
                    .body("languages.find { it.value == 'Spanish' }.count", equalTo(1))
                    .body("languages.find { it.value == 'German' }.count", equalTo(1))
                    .body("employmentTypes.find { it.value == 'full-time' }.count", equalTo(9))
                    .body("employmentTypes.find { it.value == 'contract' }.count", equalTo(1))
                    .body("careerLevels.find { it.value == 'senior' }.count", equalTo(3))
                    .body("careerLevels.find { it.value == 'mid' }.count", equalTo(1))
                    .body("careerLevels.find { it.value == 'junior' }.count", equalTo(2))
                    .body("careerLevels.find { it.value == 'lead' }.count", equalTo(1))
                    .body("compensationMin", equalTo(60000))
                    .body("compensationMax", equalTo(110000));
        }

        @Test
        @DisplayName("BE-F06: impossible combo (Stripe + Germany) → 200, cross-dimension groups empty, comp null")
        void impossibleCombinationYieldsEmptyFacets() {
            // No Stripe posting has any opening in Germany (Stripe: rows 1/2/3/6=Spain, 7=Remote,
            // 8/9=Spain+Netherlands/Spain; Germany only has rows 4/10, both Spotify, Story #1).
            // The cross-dimension intersection is empty.
            //
            // Own-dimension exclusion still applies per the drill-down contract:
            //   companies group: excludes company filter, applies location=Germany → Spotify=2 (rows 4,10)
            //   locations group: excludes location filter, applies company=Stripe →
            //     Spain=6 (1,2,3,6,8,9), Remote=1 (7), Netherlands=1 (8's additional opening)
            //
            // Groups that receive ALL active filters (languages, employmentTypes, careerLevels)
            // have zero matching rows → empty arrays.
            // Compensation range also has no priced rows in scope → both null.
            given()
                    .queryParam("company", "Stripe")
                    .queryParam("location", "Germany")
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    // own-dimension groups reflect their exclusion (not empty):
                    .body("companies.value", hasItem("Spotify"))
                    .body("companies.value", not(hasItem("Stripe")))
                    .body("companies.find { it.value == 'Spotify' }.count", equalTo(2))
                    .body("locations.value", hasItems("Spain", "Remote", "Netherlands"))
                    .body("locations.value", not(hasItem("Germany")))
                    .body("locations.find { it.value == 'Spain' }.count", equalTo(6))
                    .body("locations.find { it.value == 'Remote' }.count", equalTo(1))
                    .body("locations.find { it.value == 'Netherlands' }.count", equalTo(1))
                    // cross-dimension groups have 0 rows in scope → empty:
                    .body("languages", org.hamcrest.Matchers.empty())
                    .body("employmentTypes", org.hamcrest.Matchers.empty())
                    .body("careerLevels", org.hamcrest.Matchers.empty())
                    // no priced rows survive both filters → comp null:
                    .body("compensationMin", nullValue())
                    .body("compensationMax", nullValue());
        }
    }

    // ── BE-F07 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BE-F07: zero-count values are absent from the group")
    class BEF07 {

        @Test
        @DisplayName("location=Germany → Spanish language (0 Germany jobs with Spanish) absent from languages group")
        void zeroCountValueAbsent() {
            // Row 44 (Germany) has English+German; Spanish is only in row 11 (Spain)
            given()
                    .queryParam("location", "Germany")
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    // languages group is narrowed by location=Germany → Spanish has 0 count → absent
                    .body("languages.value", not(hasItem("Spanish")))
                    // English and German are present (both in row 44)
                    .body("languages.value", hasItems("English", "German"));
        }

        @Test
        @DisplayName("company=Stripe → German language (0 Stripe jobs with German) absent")
        void zeroCountLanguageAbsentForStripe() {
            // Stripe jobs: rows 11, 22, 33, 66 — none have German
            given()
                    .queryParam("company", "Stripe")
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    .body("languages.value", not(hasItem("German")))
                    .body("languages.value", hasItems("English", "Spanish"));
        }
    }

    // ── BE-F08 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BE-F08: comp range excludes own bounds (actual min/max, not the selection)")
    class BEF08 {

        @Test
        @DisplayName("compensationMin=80000 → returned range is still the full 60000–110000")
        void compRangeExcludesOwnBounds() {
            given()
                    .queryParam("compensationMin", 80000)
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    // comp range excludes its own dimension → shows full range ignoring compensationMin filter
                    .body("compensationMin", equalTo(60000))
                    .body("compensationMax", equalTo(110000));
        }

        @Test
        @DisplayName("compensationMax=70000 → returned range is still the full 60000–110000")
        void compRangeExcludesMaxBound() {
            given()
                    .queryParam("compensationMax", 70000)
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    .body("compensationMin", equalTo(60000))
                    .body("compensationMax", equalTo(110000));
        }

        @Test
        @DisplayName("compensationMin=80000 + location=Germany → range is only over Germany rows")
        void compRangeExcludesOwnBoundsWithOtherFilterApplied() {
            // Germany rows: row 44 (comp 65000/85000); comp filter excluded from range
            given()
                    .queryParam("compensationMin", 80000)
                    .queryParam("location", "Germany")
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    .body("compensationMin", equalTo(65000))
                    .body("compensationMax", equalTo(85000));
        }
    }

    // ── BE-F20 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BE-F20: comp range excludes own bounds even when no other filter is active")
    class BEF20 {

        @Test
        @DisplayName("compensationMin=75000 + compensationMax=95000 (no other filter) → returned range is still 60000–110000")
        void compOnlyBoundsDoNotNarrowReturnedRange() {
            // The compensation range dimension must exclude its own bounds when computing min/max.
            // With no other active filter the in-scope set is all rows; excluding comp bounds
            // means the range is computed over all priced rows → global min=60000, max=110000.
            // BE-F08 only ever pairs comp bounds with a location filter; this standalone path
            // was previously uncovered.
            given()
                    .queryParam("compensationMin", 75000)
                    .queryParam("compensationMax", 95000)
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    .body("compensationMin", equalTo(60000))
                    .body("compensationMax", equalTo(110000));
        }
    }

    // ── BE-F09 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BE-F09: compensationMin/Max null when no in-scope posting carries comp data")
    class BEF09 {

        @Test
        @DisplayName("employmentType=contract (only row 33 is contract, no comp data) → range is null")
        void compRangeNullWhenAllInScopeUnpriced() {
            // Row 33 is the only contract job, and it has NULL comp
            given()
                    .queryParam("employmentType", "contract")
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    // comp range is computed excluding employmentType; non-contract jobs have comp data
                    // BUT: comp range excludes its own dimension (compensation), not employmentType
                    // So: comp is computed with employmentType=contract applied (non-comp filter applies)
                    // → only row 33 in scope → comp is NULL
                    .body("compensationMin", nullValue())
                    .body("compensationMax", nullValue());
        }
    }

    // ── BE-F10 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BE-F10: keyword narrows all facet groups")
    class BEF10 {

        @Test
        @DisplayName("keyword=python → only row 44 matches; all groups narrow to its values")
        void keywordNarrowsAllGroups() {
            given()
                    .queryParam("keyword", "python")
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    // Only row 44 (Python Data Engineer / Spotify / Berlin / Germany / senior) matches
                    .body("companies.value", hasItem("Spotify"))
                    .body("companies.value", not(hasItem("Stripe")))
                    .body("companies.find { it.value == 'Spotify' }.count", equalTo(1))
                    .body("locations.value", hasItem("Germany"))
                    .body("locations.value", not(hasItem("Spain")))
                    .body("languages.value", hasItems("English", "German"))
                    .body("languages.value", not(hasItem("Spanish")))
                    .body("careerLevels.find { it.value == 'senior' }.count", equalTo(1));
        }
    }

    // ── BE-F11 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BE-F11: postedWithin narrows all facet groups")
    class BEF11 {

        @Test
        @DisplayName("postedWithin=today → only row 66 (firstSeenAt=NOW) matches; all groups narrow")
        void postedWithinNarrowsAllGroups() {
            given()
                    .queryParam("postedWithin", "today")
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    // Only row 66 (Stripe / Madrid / Spain / full-time / senior / 90k-110k) is recent
                    .body("companies.value", hasItem("Stripe"))
                    .body("companies.value", not(hasItem("Spotify")))
                    .body("companies.find { it.value == 'Stripe' }.count", equalTo(1))
                    .body("locations.value", hasItem("Spain"))
                    .body("locations.value", not(hasItem("Germany")))
                    .body("employmentTypes.value", hasItem("full-time"))
                    .body("employmentTypes.value", not(hasItem("contract")))
                    .body("careerLevels.value", hasItem("senior"))
                    .body("careerLevels.find { it.value == 'senior' }.count", equalTo(1))
                    .body("compensationMin", equalTo(90000))
                    .body("compensationMax", equalTo(110000));
        }
    }

    // ── BE-F12 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BE-F12: keyword + location combo narrows all groups")
    class BEF12 {

        @Test
        @DisplayName("keyword=java + location=Germany → only row 44 is Python (no match) → empty groups")
        void keywordAndLocationComboNoMatch() {
            // keyword=java does not match row 44 (title=Python Data Engineer, description=Data pipelines on AWS)
            // and location=Germany only has row 44 → intersection is empty
            given()
                    .queryParam("keyword", "java")
                    .queryParam("location", "Germany")
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    // companies, locations etc. should have no entries because no job matches
                    // BUT: each group excludes its own dimension:
                    // companies group: keyword=java + location=Germany applied → 0 rows → empty
                    // locations group: keyword=java (no Germany java jobs) → locations excludes location
                    //   so location filter is off, but keyword=java still applied → Spain rows with java
                    .body("locations.value", hasItems("Spain"))
                    .body("locations.value", not(hasItem("Germany")));
        }

        @Test
        @DisplayName("keyword=java + location=Spain → narrows to Spain-Java jobs; Germany absent")
        void keywordAndLocationComboNarrows() {
            // Java jobs in Spain: rows 11 (Madrid), 22 (Barcelona), 55 (Madrid), 66 (Madrid)
            // Titles/desc containing "java": rows 11, 22, 55, 66
            given()
                    .queryParam("keyword", "java")
                    .queryParam("location", "Spain")
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    // companies: excludes company, applies keyword=java + location=Spain
                    //  → rows 11,22,55,66 match keyword=java AND location=Spain → Stripe(11,22,66)=3, Spotify(55)=1
                    .body("companies.find { it.value == 'Stripe' }.count", equalTo(3))
                    .body("companies.find { it.value == 'Spotify' }.count", equalTo(1))
                    // locations: excludes location, applies keyword=java
                    //  → java keyword matches rows 11,22,55,66 (all Spain) → Spain=4; no Germany java jobs
                    .body("locations.find { it.value == 'Spain' }.count", equalTo(4))
                    .body("locations.value", not(hasItem("Germany")));
        }
    }

    // ── BE-F13 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BE-F13: bad enum → 400 with {error, message}")
    class BEF13 {

        @Test
        @DisplayName("employmentType=bogus → 400 with error and message fields")
        void badEmploymentTypeEnum() {
            given()
                    .queryParam("employmentType", "bogus")
                    .when().get(FACETS)
                    .then()
                    .statusCode(400)
                    .body("error", equalTo("Bad Request"))
                    .body("message", org.hamcrest.Matchers.notNullValue());
        }

        @Test
        @DisplayName("careerLevel=invalid → 400 with error and message fields")
        void badCareerLevelEnum() {
            given()
                    .queryParam("careerLevel", "invalid")
                    .when().get(FACETS)
                    .then()
                    .statusCode(400)
                    .body("error", equalTo("Bad Request"))
                    .body("message", org.hamcrest.Matchers.notNullValue());
        }

        @Test
        @DisplayName("postedWithin=yesterday → 400 with error and message fields")
        void badPostedWithinEnum() {
            given()
                    .queryParam("postedWithin", "yesterday")
                    .when().get(FACETS)
                    .then()
                    .statusCode(400)
                    .body("error", equalTo("Bad Request"))
                    .body("message", org.hamcrest.Matchers.notNullValue());
        }
    }

    // ── BE-F14 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BE-F14: sort/page/size are ignored → 200")
    class BEF14 {

        @Test
        @DisplayName("sort + page + size params are silently ignored → 200 with full facets")
        void sortPageSizeIgnored() {
            given()
                    .queryParam("sort", "newest")
                    .queryParam("page", 0)
                    .queryParam("size", 10)
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    .body("companies.find { it.value == 'Stripe' }.count", equalTo(7))
                    .body("compensationMin", equalTo(60000));
        }
    }

    // ── BE-F18 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BE-F18: Remote bucket appears in locations; own-dimension keeps it visible under location filter")
    class BEF18 {

        @Test
        @DisplayName("no filter → locations contains Remote=2 (Story #1: primary + additional contributor)")
        void remoteAppearsWithNoFilter() {
            given()
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    // Row 7 has country='Remote' (primary); row 10 has Remote as an additional
                    // opening (Story #1) → synthetic Remote bucket counts both postings once each
                    .body("locations.value", hasItem("Remote"))
                    .body("locations.find { it.value == 'Remote' }.count", equalTo(2));
        }

        @Test
        @DisplayName("location=Spain → locations group (own-dimension excluded) still shows Remote")
        void remoteStillVisibleWhenLocationFilterApplied() {
            given()
                    .queryParam("location", "Spain")
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    // locations group excludes the location filter → shows table-wide counts
                    // Remote bucket must still appear with count=2 (rows 7 + 10, Story #1)
                    .body("locations.value", hasItem("Remote"))
                    .body("locations.find { it.value == 'Remote' }.count", equalTo(2))
                    // Spain and Germany are also still present at full table-wide counts
                    .body("locations.find { it.value == 'Spain' }.count", equalTo(7))
                    .body("locations.find { it.value == 'Germany' }.count", equalTo(2));
        }
    }

    // ── BE-F19 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BE-F19: language own-dimension — languages group uses table-wide counts when language filter active")
    class BEF19 {

        @Test
        @DisplayName("language=English → languages group still lists all languages with table-wide counts; other groups narrow")
        void languageOwnDimensionExcluded() {
            given()
                    .queryParam("language", "English")
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    // languages group excludes the language filter (own-dimension) →
                    // unnest path still counts all rows regardless of English filter
                    .body("languages.find { it.value == 'English' }.count", equalTo(7))
                    .body("languages.find { it.value == 'Spanish' }.count", equalTo(1))
                    .body("languages.find { it.value == 'German' }.count", equalTo(1))
                    // other groups ARE narrowed: English-language rows are all 7 rows →
                    // companies: Stripe=5, Spotify=2
                    .body("companies.find { it.value == 'Stripe' }.count", equalTo(5))
                    .body("companies.find { it.value == 'Spotify' }.count", equalTo(2))
                    // locations: Spain=5, Germany=1, Remote=1 (all rows have English)
                    .body("locations.find { it.value == 'Spain' }.count", equalTo(5))
                    .body("locations.find { it.value == 'Germany' }.count", equalTo(1))
                    .body("locations.find { it.value == 'Remote' }.count", equalTo(1));
        }

        @Test
        @DisplayName("language=Spanish → languages group still shows English+German+Spanish; other groups narrow to Spanish rows")
        void languageSpanishOwnDimensionExcluded() {
            given()
                    .queryParam("language", "Spanish")
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    // languages group excludes the language filter → all languages appear with table-wide counts
                    .body("languages.find { it.value == 'English' }.count", equalTo(7))
                    .body("languages.find { it.value == 'Spanish' }.count", equalTo(1))
                    .body("languages.find { it.value == 'German' }.count", equalTo(1))
                    // other groups are narrowed to Spanish rows: only row 11 (Stripe / Madrid / Spain / senior)
                    .body("companies.find { it.value == 'Stripe' }.count", equalTo(1))
                    .body("companies.value", not(hasItem("Spotify")))
                    .body("locations.find { it.value == 'Spain' }.count", equalTo(1))
                    .body("locations.value", not(hasItem("Germany")))
                    .body("locations.value", not(hasItem("Remote")));
        }
    }

    // ── Story #332 (ADR 0020): facet-response cache correctness/regression ────

    @Nested
    @DisplayName("FC332-C-01 (AC-332-1/6): location=Germany called twice back-to-back are identical, matching BE-F01's known values")
    class FC332C01 {

        @Test
        @DisplayName("both responses identical to each other and to BE-F01's known values")
        void repeatedLocationGermanyIsIdenticalAndCorrect() {
            String first = given().queryParam("location", "Germany").when().get(FACETS)
                    .then().statusCode(200)
                    .body("companies.find { it.value == 'Spotify' }.count", equalTo(2))
                    .body("locations.find { it.value == 'Spain' }.count", equalTo(7))
                    .body("locations.find { it.value == 'Germany' }.count", equalTo(2))
                    .extract().asString();

            String second = given().queryParam("location", "Germany").when().get(FACETS)
                    .then().statusCode(200)
                    .extract().asString();

            assertThat(second).isEqualTo(first);
        }
    }

    @Nested
    @DisplayName("FC332-C-02 (AC-332-2/6): company=Spotify called twice are identical, matching BE-F02's own-dimension values")
    class FC332C02 {

        @Test
        @DisplayName("both responses identical to each other and to BE-F02's known values")
        void repeatedCompanySpotifyIsIdenticalAndCorrect() {
            String first = given().queryParam("company", "Spotify").when().get(FACETS)
                    .then().statusCode(200)
                    .body("companies.find { it.value == 'Stripe' }.count", equalTo(7))
                    .body("companies.find { it.value == 'Spotify' }.count", equalTo(3))
                    .extract().asString();

            String second = given().queryParam("company", "Spotify").when().get(FACETS)
                    .then().statusCode(200)
                    .extract().asString();

            assertThat(second).isEqualTo(first);
        }
    }

    @Nested
    @DisplayName("FC332-C-03 (AC-332-3): location=Germany called twice -> zero-count value omission (Spanish) unaffected by caching")
    class FC332C03 {

        @Test
        @DisplayName("languages omits Spanish, includes English/German, both calls")
        void zeroCountLanguageOmittedBothCalls() {
            for (int i = 0; i < 2; i++) {
                given().queryParam("location", "Germany").when().get(FACETS)
                        .then().statusCode(200)
                        .body("languages.value", not(hasItem("Spanish")))
                        .body("languages.value", hasItems("English", "German"));
            }
        }
    }

    @Nested
    @DisplayName("FC332-C-04 (AC-332-4): no-params request called twice -> companies array element order identical")
    class FC332C04 {

        @Test
        @DisplayName("ordering of facet values is unaffected by caching")
        void companiesOrderIdenticalAcrossRepeatedCalls() {
            List<String> first = given().when().get(FACETS).then().statusCode(200)
                    .extract().jsonPath().getList("companies.value", String.class);
            List<String> second = given().when().get(FACETS).then().statusCode(200)
                    .extract().jsonPath().getList("companies.value", String.class);

            assertThat(second).containsExactlyElementsOf(first);
        }
    }

    @Nested
    @DisplayName("FC332-C-05 (AC-332-5): employmentType=contract called twice -> compensation range null both times")
    class FC332C05 {

        @Test
        @DisplayName("compensationMin/compensationMax both null, both calls")
        void compRangeNullBothCalls() {
            for (int i = 0; i < 2; i++) {
                given().queryParam("employmentType", "contract").when().get(FACETS)
                        .then().statusCode(200)
                        .body("compensationMin", nullValue())
                        .body("compensationMax", nullValue());
            }
        }
    }

    @Nested
    @DisplayName("FC332-C-06 (AC-332-7): facets stay identical across interleaved GET /jobs page/size/sort variations for the same filter combo")
    class FC332C06 {

        @Test
        @DisplayName("location=Spain facets 3x, interleaved with GET /jobs?location=Spain using different page/size/sort")
        void facetsUnaffectedByAccompanyingSearchPagination() {
            String firstFacets = given().queryParam("location", "Spain").when().get(FACETS)
                    .then().statusCode(200).extract().asString();

            given().queryParam("location", "Spain").queryParam("page", 0).queryParam("size", 5)
                    .queryParam("sort", "newest").when().get("/jobs")
                    .then().statusCode(200);

            String secondFacets = given().queryParam("location", "Spain").when().get(FACETS)
                    .then().statusCode(200).extract().asString();

            given().queryParam("location", "Spain").queryParam("page", 1).queryParam("size", 3)
                    .queryParam("sort", "salary-desc").when().get("/jobs")
                    .then().statusCode(200);

            String thirdFacets = given().queryParam("location", "Spain").when().get(FACETS)
                    .then().statusCode(200).extract().asString();

            assertThat(secondFacets).isEqualTo(firstFacets);
            assertThat(thirdFacets).isEqualTo(firstFacets);
        }
    }

    @Nested
    @DisplayName("FC332-C-07 (AC-332-13): alternate location=Germany (A) -> company=Spotify (B) -> location=Germany again (A)")
    class FC332C07 {

        @Test
        @DisplayName("returning to a previously-seen filter combination reuses its own cached value, not B's")
        void alternatingCombosReturnToOwnValue() {
            String firstA = given().queryParam("location", "Germany").when().get(FACETS)
                    .then().statusCode(200).extract().asString();

            String responseB = given().queryParam("company", "Spotify").when().get(FACETS)
                    .then().statusCode(200).extract().asString();

            String secondA = given().queryParam("location", "Germany").when().get(FACETS)
                    .then().statusCode(200).extract().asString();

            assertThat(secondA).isEqualTo(firstA);
            assertThat(secondA).isNotEqualTo(responseB);
        }
    }

    @Nested
    @DisplayName("FC332-C-08 (AC-332-12): location=Germany then location=Spain -> second reflects Spain-scoped values, no carryover")
    class FC332C08 {

        @Test
        @DisplayName("changing the location filter yields the new combination's own values")
        void changingLocationFilterYieldsDistinctValues() {
            given().queryParam("location", "Germany").when().get(FACETS)
                    .then().statusCode(200)
                    .body("companies.value", hasItem("Spotify"))
                    .body("companies.value", not(hasItem("Stripe")));

            given().queryParam("location", "Spain").when().get(FACETS)
                    .then().statusCode(200)
                    .body("companies.value", hasItems("Stripe", "Spotify"));
        }
    }

    @Nested
    @DisplayName("FC332-C-09 (AC-332-14): keyword matching zero rows called twice -> 200, every group empty, comp null, identical both times")
    class FC332C09 {

        @Test
        @DisplayName("keyword=zzznomatch -> empty groups + null comp, identical fresh and cached")
        void noMatchKeywordYieldsEmptyFacetsIdenticalBothTimes() {
            String first = given().queryParam("keyword", "zzznomatch").when().get(FACETS)
                    .then().statusCode(200)
                    .body("companies", org.hamcrest.Matchers.empty())
                    .body("locations", org.hamcrest.Matchers.empty())
                    .body("languages", org.hamcrest.Matchers.empty())
                    .body("employmentTypes", org.hamcrest.Matchers.empty())
                    .body("careerLevels", org.hamcrest.Matchers.empty())
                    .body("compensationMin", nullValue())
                    .body("compensationMax", nullValue())
                    .extract().asString();

            String second = given().queryParam("keyword", "zzznomatch").when().get(FACETS)
                    .then().statusCode(200)
                    .extract().asString();

            assertThat(second).isEqualTo(first);
        }
    }

    @Nested
    @DisplayName("FC332-C-12 (AC-332-16): employmentType=contract called twice -> compensationMin/compensationMax keys explicitly present with null value")
    class FC332C12 {

        @Test
        @DisplayName("comp keys present with null value, never omitted, both calls")
        void compKeysPresentWithNullValue() {
            for (int i = 0; i < 2; i++) {
                Map<String, Object> body = given().queryParam("employmentType", "contract").when().get(FACETS)
                        .then().statusCode(200)
                        .extract().jsonPath().getMap("");

                assertThat(body).containsKey("compensationMin");
                assertThat(body).containsKey("compensationMax");
                assertThat(body.get("compensationMin")).isNull();
                assertThat(body.get("compensationMax")).isNull();
            }
        }
    }

    @Nested
    @DisplayName("FC332-C-16 (AC-332-19): top-level JSON key set is exactly the documented JobFacets shape")
    class FC332C16 {

        @Test
        @DisplayName("no filters -> exactly {companies, locations, languages, employmentTypes, careerLevels, compensationMin, compensationMax}")
        void topLevelKeySetIsExact() {
            Map<String, Object> body = given().when().get(FACETS).then().statusCode(200)
                    .extract().jsonPath().getMap("");

            assertThat(body.keySet()).containsExactlyInAnyOrder(
                    "companies", "locations", "languages", "employmentTypes", "careerLevels",
                    "compensationMin", "compensationMax");
        }
    }

    @Nested
    @DisplayName("FC332-C-17/18/19 (AC-332-20): invalid facet filter enum values called twice -> same 400 shape both times, never cached/polluted")
    class FC332C17C18C19 {

        @Test
        @DisplayName("FC332-C-17: employmentType=bogus called twice -> 400 with {error, message}, identical shape both times")
        void badEmploymentTypeCalledTwice() {
            for (int i = 0; i < 2; i++) {
                given().queryParam("employmentType", "bogus").when().get(FACETS)
                        .then().statusCode(400)
                        .body("error", equalTo("Bad Request"))
                        .body("message", org.hamcrest.Matchers.notNullValue());
            }
        }

        @Test
        @DisplayName("FC332-C-18: careerLevel=invalid called twice -> 400 with {error, message}, identical shape both times")
        void badCareerLevelCalledTwice() {
            for (int i = 0; i < 2; i++) {
                given().queryParam("careerLevel", "invalid").when().get(FACETS)
                        .then().statusCode(400)
                        .body("error", equalTo("Bad Request"))
                        .body("message", org.hamcrest.Matchers.notNullValue());
            }
        }

        @Test
        @DisplayName("FC332-C-19: postedWithin=yesterday called twice -> 400 with {error, message}, identical shape both times")
        void badPostedWithinCalledTwice() {
            for (int i = 0; i < 2; i++) {
                given().queryParam("postedWithin", "yesterday").when().get(FACETS)
                        .then().statusCode(400)
                        .body("error", equalTo("Bad Request"))
                        .body("message", org.hamcrest.Matchers.notNullValue());
            }
        }
    }
}
