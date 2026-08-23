package com.davidcreate.jobhub.job.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Component tests for Story #1 (#292): multiple locations per job post, exposed via
 * {@code JobPostResponse.locations[]}, the multi-opening {@code location} filter, and the
 * multi-opening {@code locations} facet (ADR 0017).
 *
 * <p>Its own top-level class per the QAE doc (not crammed into {@link JobResourceComponentTest}
 * or {@link JobFacetsDrillDownComponentTest}) since it needs its own seed reasoning documented.
 * Uses the same {@code BASE}-constant pattern (no {@code @TestHTTPEndpoint} + {@code @Nested}).
 *
 * <p>Seed layout locked for Story #1 (see {@code test-seeds.sql} for the full comment):
 * <pre>
 *  id  company  primary opening        additional openings
 *  8   Stripe   Barcelona, Spain       Amsterdam, Netherlands
 *  9   Stripe   Barcelona, Spain       Madrid, Spain (same country, second city)
 *  10  Spotify  Berlin, Germany        Remote
 * </pre>
 * Row 7 (existing) is Remote-primary, no additional openings.
 *
 * <p>Table-wide totals after the additions (10 postings, 13 job_post_location rows):
 * locations facet: Spain=7, Germany=2, Netherlands=1, Remote=2.
 */
@QuarkusTest
@DisplayName("Story #1 (#292): multiple locations per job post")
class JobLocationsComponentTest {

    private static final String JOBS = "/jobs";
    private static final String FACETS = "/jobs/facets";

    private static final UUID ROW_1_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ROW_3_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ROW_4_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID ROW_6_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID ROW_7_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID ROW_8_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID ROW_9_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID ROW_10_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID ROW_11_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Nested
    @DisplayName("RETURN — GET /jobs/{id} and GET /jobs")
    class Return {

        @Test
        @DisplayName("QAE-JOB-RETURN-1: multi-opening post returns 2 locations, primary first")
        void multiOpeningPostReturnsLocationsPrimaryFirst() {
            given()
                    .pathParam("id", ROW_8_ID)
                    .when().get(JOBS + "/{id}")
                    .then()
                    .statusCode(200)
                    .body("locations.size()", equalTo(2))
                    .body("locations[0].primary", equalTo(true))
                    .body("locations[0].city", equalTo("Barcelona"))
                    .body("locations[0].country", equalTo("Spain"))
                    .body("locations[1].primary", equalTo(false))
                    .body("locations[1].city", equalTo("Amsterdam"))
                    .body("locations[1].country", equalTo("Netherlands"));
        }

        @Test
        @DisplayName("QAE-JOB-RETURN-2: single-opening seed row — locations[0] equals location")
        void singleOpeningRowLocationsMatchesLocationString() {
            given()
                    .pathParam("id", ROW_1_ID)
                    .when().get(JOBS + "/{id}")
                    .then()
                    .statusCode(200)
                    .body("location", equalTo("Madrid, Spain"))
                    .body("locations.size()", equalTo(1))
                    .body("locations[0].primary", equalTo(true))
                    .body("locations[0].city", equalTo("Madrid"))
                    .body("locations[0].country", equalTo("Spain"));
        }

        @Test
        @DisplayName("QAE-JOB-RETURN-3: 404 on missing post keeps the existing {error, message} shape")
        void missingPostStill404sWithExistingShape() {
            given()
                    .pathParam("id", UUID.randomUUID())
                    .when().get(JOBS + "/{id}")
                    .then()
                    .statusCode(404)
                    .body("error", notNullValue())
                    .body("message", notNullValue());
        }

        @Test
        @DisplayName("QAE-JOB-RETURN-4: Remote-primary seed row returns Remote in both location and locations")
        void remotePrimaryRowReturnsRemoteEverywhere() {
            given()
                    .pathParam("id", ROW_7_ID)
                    .when().get(JOBS + "/{id}")
                    .then()
                    .statusCode(200)
                    .body("location", equalTo("Remote"))
                    .body("locations.size()", equalTo(1))
                    .body("locations[0].primary", equalTo(true))
                    .body("locations[0].country", equalTo("Remote"));
        }

        @Test
        @DisplayName("TC-319-JOB-RETURN-01: zero-location posting returns gracefully alongside genuine multi-opening data")
        void zeroLocationPostingReturnsEmptyLocationsArray() {
            given()
                    .pathParam("id", ROW_11_ID)
                    .when().get(JOBS + "/{id}")
                    .then()
                    .statusCode(200)
                    .body("location", nullValue())
                    .body("locations.size()", equalTo(0));
        }

        @Test
        @DisplayName("QAE-JOB-RETURN-5: GET /jobs list entries carry the same locations as GET /jobs/{id}")
        void listEntryLocationsMatchDetailEntry() {
            var detail = given()
                    .pathParam("id", ROW_8_ID)
                    .when().get(JOBS + "/{id}")
                    .then()
                    .statusCode(200)
                    .extract().jsonPath();

            given()
                    .queryParam("keyword", "Multi-Location")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content.size()", equalTo(1))
                    .body("content[0].locations.size()", equalTo(detail.getList("locations").size()))
                    .body("content[0].locations[0].primary", equalTo(true))
                    .body("content[0].locations[0].city", equalTo(detail.getString("locations[0].city")))
                    .body("content[0].locations[0].country", equalTo(detail.getString("locations[0].country")))
                    .body("content[0].locations[1].city", equalTo(detail.getString("locations[1].city")))
                    .body("content[0].locations[1].country", equalTo(detail.getString("locations[1].country")));
        }
    }

    @Nested
    @DisplayName("FILTER — GET /jobs?location=")
    class Filter {

        @Test
        @DisplayName("QAE-JOB-FILTER-1: filtering by a non-primary opening still returns the post")
        void filterByNonPrimaryOpeningReturnsPost() {
            given()
                    .queryParam("location", "Netherlands")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content.id", hasItems(ROW_8_ID.toString()));
        }

        @Test
        @DisplayName("QAE-JOB-FILTER-2: a post with several matching openings is returned exactly once")
        void postWithSeveralMatchingOpeningsReturnedOnce() {
            given()
                    .queryParam("location", "Spain")
                    .queryParam("location", "Netherlands")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content.findAll { it.id == '" + ROW_8_ID + "' }.size()", equalTo(1));
        }

        @Test
        @DisplayName("QAE-JOB-FILTER-3: filtering by the primary opening still works (regression)")
        void filterByPrimaryOpeningStillWorks() {
            given()
                    .queryParam("location", "Spain")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content.id", hasItems(ROW_1_ID.toString()));
        }

        @Test
        @DisplayName("QAE-JOB-FILTER-4: filtering by Remote matches a post whose Remote is an additional opening")
        void filterByRemoteMatchesAdditionalOpening() {
            given()
                    .queryParam("location", "Remote")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content.id", hasItems(ROW_7_ID.toString(), ROW_10_ID.toString()))
                    .body("totalElements", equalTo(2));
        }

        @Test
        @DisplayName("QAE-JOB-FILTER-5: no match yields 200 + empty content, not an error")
        void noMatchYieldsEmptyContent() {
            given()
                    .queryParam("location", "Freedonia")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content", empty())
                    .body("totalElements", equalTo(0));
        }

        @Test
        @DisplayName("TC-319-JOB-FILTER-01: repeatable location matches EITHER of two distinct postings, no cross-contamination")
        void repeatableLocationMatchesEitherPostingWithoutCrossContamination() {
            // row 4 (Germany only), row 7 (Remote only), row 10 (Berlin/Germany + additional
            // Remote, matches both incidentally) — each of row 4/row 7 must appear exactly once.
            given()
                    .queryParam("location", "Germany")
                    .queryParam("location", "Remote")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content.findAll { it.id == '" + ROW_4_ID + "' }.size()", equalTo(1))
                    .body("content.findAll { it.id == '" + ROW_7_ID + "' }.size()", equalTo(1))
                    .body("totalElements", equalTo(3));
        }

        @Test
        @DisplayName("TC-319-JOB-FILTER-02: comma-form location value matches a non-primary opening")
        void commaFormLocationValueMatchesNonPrimaryOpening() {
            given()
                    .queryParam("location", "Amsterdam, Netherlands")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content.id", hasItems(ROW_8_ID.toString()));
        }

        @Test
        @DisplayName("TC-319-JOB-FILTER-03: a zero-location posting never matches any filter value")
        void zeroLocationPostingNeverMatchesLocationFilter() {
            given()
                    .queryParam("location", "Spain")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content.id", not(hasItem(ROW_11_ID.toString())));
        }
    }

    @Nested
    @DisplayName("FACET — GET /jobs/facets locations group")
    class Facet {

        @Test
        @DisplayName("QAE-JOB-FACET-1: same-country, two-city post (row 9) counts once for Spain")
        void sameCountryTwoCityPostCountsOnce() {
            given()
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    .body("locations.find { it.value == 'Spain' }.count", equalTo(7));
        }

        @Test
        @DisplayName("QAE-JOB-FACET-2: a post with openings in two countries (row 8) counts once per country")
        void postWithTwoCountriesCountsOncePerCountry() {
            given()
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    .body("locations.find { it.value == 'Netherlands' }.count", equalTo(1))
                    .body("locations.find { it.value == 'Spain' }.count", equalTo(7));
        }

        @Test
        @DisplayName("QAE-JOB-FACET-3: Remote bucket counts postings with Remote anywhere in their opening set")
        void remoteBucketCountsPrimaryAndAdditionalContributors() {
            given()
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    .body("locations.find { it.value == 'Remote' }.count", equalTo(2));
        }

        @Test
        @DisplayName("QAE-JOB-FACET-4: facet sum across countries exceeds total posting count (documented, not a bug)")
        void facetSumExceedsTotalPostingCount() {
            var facets = given()
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    .extract().jsonPath();

            java.util.List<Object> rawCounts = facets.getList("locations.count");
            long facetSum = rawCounts.stream()
                    .mapToLong(v -> ((Number) v).longValue())
                    .sum();

            var searchBody = given()
                    .queryParam("size", 100)
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .extract().jsonPath();
            long totalElements = searchBody.getLong("totalElements");
            // Postings that actually carry a location signal (location != null): rows 1-10.
            // Rows 11 (story #319) and 12/13/14 (story #428) are all zero-location rows -
            // they inflate totalElements without contributing to any location bucket, so the
            // meaningful "facet sum exceeds posting count" comparison below is scoped to
            // location-bearing postings only, not the raw unfiltered total.
            int locatablePostingCount = searchBody
                    .getList("content.findAll { it.location != null }").size();

            // Spain=7 + Germany=2 + Netherlands=1 + Remote=2 = 12 (per-country facet counts,
            // §2.2 of the QAE doc) versus 10 location-bearing postings - the sum legitimately
            // exceeds the location-bearing posting count because rows 8/9/10 each contribute
            // to more than one bucket. Both the facetSum (12) and the location-bearing count
            // (10) are unchanged by story #428: none of the three new rows carry any location
            // signal, they only add to the unfiltered totalElements (11 -> 14).
            org.assertj.core.api.Assertions.assertThat(facetSum).isGreaterThan(locatablePostingCount);
            org.assertj.core.api.Assertions.assertThat(totalElements).isEqualTo(14);
            org.assertj.core.api.Assertions.assertThat(locatablePostingCount).isEqualTo(10);
            org.assertj.core.api.Assertions.assertThat(facetSum).isEqualTo(12);
        }

        @Test
        @DisplayName("TC-319-JOB-FACET-01: zero-location posting contributes to no country/Remote bucket")
        void zeroLocationPostingContributesToNoBucket() {
            given()
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    .body("locations.find { it.value == 'Spain' }.count", equalTo(7))
                    .body("locations.find { it.value == 'Germany' }.count", equalTo(2))
                    .body("locations.find { it.value == 'Netherlands' }.count", equalTo(1))
                    .body("locations.find { it.value == 'Remote' }.count", equalTo(2))
                    .body("locations.findAll { it.value == null || it.value.toString().trim().isEmpty() }.size()",
                            equalTo(0));
        }

        @Test
        @DisplayName("QAE-JOB-FACET-5: drill-down — locations group still excludes its own active filter")
        void drillDownExcludesOwnFilterWithMultiOpeningData() {
            given()
                    .queryParam("location", "Netherlands")
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    // locations group excludes location=Netherlands → table-wide counts, unaffected
                    .body("locations.find { it.value == 'Spain' }.count", equalTo(7))
                    .body("locations.find { it.value == 'Germany' }.count", equalTo(2))
                    .body("locations.find { it.value == 'Remote' }.count", equalTo(2))
                    // companies group IS narrowed by location=Netherlands → only row 8 (Stripe)
                    .body("companies.find { it.value == 'Stripe' }.count", equalTo(1))
                    .body("companies.value", org.hamcrest.Matchers.not(hasItems("Spotify")));
        }
    }

    @Nested
    @DisplayName("4xx — no new error paths introduced by Story #1")
    class FourXx {

        @Test
        @DisplayName("QAE-JOB-4XX-1: blank location value does not 400 (free-text param, contract unchanged)")
        void blankLocationValueDoesNot400() {
            given()
                    .queryParam("location", " ")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200);
        }

        @Test
        @DisplayName("QAE-JOB-4XX-2: existing size validation unaffected by the locations additive change")
        void existingSizeValidationUnaffected() {
            given()
                    .queryParam("size", 101)
                    .when().get(JOBS)
                    .then()
                    .statusCode(400)
                    .body("error", notNullValue())
                    .body("message", notNullValue());
        }

        @Test
        @DisplayName("QAE-JOB-4XX-3: GET /jobs/facets bad enum still 400 with multi-opening seed data present")
        void facetsBadEnumStill400() {
            given()
                    .queryParam("employmentType", "bogus")
                    .when().get(FACETS)
                    .then()
                    .statusCode(400)
                    .body("error", equalTo("Bad Request"))
                    .body("message", notNullValue());
        }
    }
}
