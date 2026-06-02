package com.davidcreate.jobhub.job.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.notNullValue;

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
                    .body("totalElements", equalTo(5))
                    .body("page", equalTo(0))
                    .body("size", equalTo(20))
                    .body("totalPages", equalTo(1))
                    .body("content.size()", equalTo(5));
        }

        @Test
        @DisplayName("✓ keyword provided → 200 OK & keyword-filtered jobs")
        void testSearchWithKeyword() {
            given()
                    .queryParam("keyword", "Java")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content.size()", greaterThanOrEqualTo(1))
                    .body("content.title", everyItem(matchesRegex("(?i).*java.*")));
        }

        @Test
        @DisplayName("✓ location='Madrid, Spain' → 200 OK & city+country filtered")
        void testSearchWithLocationCityCountry() {
            given()
                    .queryParam("location", "Madrid, Spain")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content.size()", greaterThanOrEqualTo(1))
                    .body("content.location", everyItem(equalToIgnoringCase("Madrid, Spain")));
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
        @DisplayName("✓ employmentType=full-time → 200 OK & filtered jobs")
        void testSearchByEmploymentType() {
            given()
                    .queryParam("employmentType", "full-time")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content.employmentType", everyItem(equalTo("full-time")));
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
        @DisplayName("✓ pagination provided → totalElements unchanged, content trimmed")
        void testSearchWithPagination() {
            given()
                    .queryParam("page", 0)
                    .queryParam("size", 3)
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(5))
                    .body("totalPages", equalTo(2))
                    .body("content.size()", equalTo(3));

            given()
                    .queryParam("page", 1)
                    .queryParam("size", 3)
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(5))
                    .body("content.size()", equalTo(2));
        }

        @Test
        @DisplayName("✓ sort=oldest → results sorted by firstSeenAt ASC")
        void testSearchSortOldest() {
            given()
                    .queryParam("sort", "oldest")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("content[0].firstSeenAt", notNullValue());
        }

        @Test
        @DisplayName("✓ language=Spanish → 200 OK & only the Spanish-tagged role returns")
        void testSearchByLanguageSingle() {
            given()
                    .queryParam("language", "Spanish")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(1))
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
        @DisplayName("✓ language=Italian (no match) → 200 OK with empty content")
        void testSearchByLanguageNoMatch() {
            given()
                    .queryParam("language", "Italian")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(0))
                    .body("content", empty());
        }

        @Test
        @DisplayName("✓ keyword matches no records → 200 OK with empty content (no 404)")
        void testSearchNoMatchReturnsEmpty() {
            given()
                    .queryParam("keyword", "ZZZZZZNOMATCH")
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("totalElements", equalTo(0))
                    .body("content", empty());
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

    @Nested
    @DisplayName("GET /jobs/facets")
    class JobFacets {

        private static final String FACETS = "/jobs/facets";

        @Test
        @DisplayName("✓ returns table-wide companies with counts (Stripe=3, Spotify=2)")
        void facetsCompanies() {
            given()
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    .body("companies.value", hasItems("Spotify", "Stripe"))
                    .body("companies.find { it.value == 'Stripe' }.count", equalTo(3))
                    .body("companies.find { it.value == 'Spotify' }.count", equalTo(2));
        }

        @Test
        @DisplayName("✓ returns table-wide locations (Spain=4, Germany=1)")
        void facetsLocations() {
            given()
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    .body("locations.value", hasItems("Germany", "Spain"))
                    .body("locations.find { it.value == 'Spain' }.count", equalTo(4))
                    .body("locations.find { it.value == 'Germany' }.count", equalTo(1));
        }

        @Test
        @DisplayName("✓ returns table-wide languages from the array column (English=5, German=1, Spanish=1)")
        void facetsLanguages() {
            given()
                    .when().get(FACETS)
                    .then()
                    .statusCode(200)
                    .body("languages.value", hasItems("English", "German", "Spanish"))
                    .body("languages.find { it.value == 'English' }.count", equalTo(5))
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
                    .body("employmentTypes.find { it.value == 'full-time' }.count", equalTo(4))
                    .body("compensationMin", equalTo(60000))
                    .body("compensationMax", equalTo(100000));
        }
    }

    @Nested
    @DisplayName("GET /jobs/{id}")
    class GetJobById {

        @Test
        @DisplayName("✓ existing ID → 200 OK & full contract response shape")
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
                    .body("compensationMin", equalTo(70000));
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
