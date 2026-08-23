package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.domain.model.JobPost;
import com.davidcreate.jobhub.job.domain.model.JobSearchQuery;
import com.davidcreate.jobhub.job.domain.model.JobSortOrder;
import com.davidcreate.jobhub.job.domain.port.in.SearchJobsUseCase;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;
import jakarta.transaction.Transactional;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Story #333: eliminate the N+1 on {@code JobPostPanacheRepository.search()} caused by lazily
 * loading {@code JobPostEntity.locations} once per row after the main query returns.
 *
 * <p>Statement counting follows the QAE mechanism (#404): {@code quarkus.hibernate-orm.statistics}
 * is on for the test profile only (see {@code src/test/resources/application.properties}),
 * {@link SessionFactory#getStatistics()} is read immediately after a direct
 * {@link SearchJobsUseCase#search(JobSearchQuery)} call (never through the HTTP resource, never
 * through {@code count()}, which is cached per story #331/#332 and would confound the
 * measurement).
 */
@QuarkusTest
@DisplayName("Story #333 (#405): GET /jobs list N+1 elimination on job_post_location")
class JobPostSearchN1ComponentTest {

    private static final String JOBS = "/jobs";

    private static final UUID ROW_8_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID ROW_9_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID ROW_10_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID ROW_11_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Inject
    SearchJobsUseCase searchJobsUseCase;

    @Inject
    EntityManagerFactory entityManagerFactory;

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    // Note for future maintainers: at Hibernate 7.3.2 (this module's current ORM version), an
    // implicit collection batch-fetch default already flattens job_post_location loading at this
    // seed's scale (11 rows), even without JobPostEntity.locations' explicit @BatchSize(100); this
    // test does not go red on that ORM version if the annotation is removed. It still earns its
    // keep as a regression guard: it stays green only as long as SOME bound exists (today, the
    // explicit @BatchSize), and would catch a real N+1 reappearing if a future Hibernate/Quarkus
    // upgrade changes or removes that implicit default while the explicit annotation is also gone.
    @Test
    @Transactional
    @DisplayName("AC-333-N1-1: statement count for search() is flat and bounded regardless of page size")
    void statementCountIsFlatAndBoundedAcrossPageSizes() {
        Statistics stats = statistics();
        stats.setStatisticsEnabled(true);

        stats.clear();
        List<JobPost> smallPage = searchJobsUseCase.search(
                JobSearchQuery.builder().sort(JobSortOrder.OLDEST).page(0).size(3).build());
        long smallPageStatementCount = stats.getPrepareStatementCount();

        stats.clear();
        List<JobPost> fullPage = searchJobsUseCase.search(
                JobSearchQuery.builder().sort(JobSortOrder.OLDEST).page(0).size(14).build());
        long fullPageStatementCount = stats.getPrepareStatementCount();

        assertThat(smallPage).hasSize(3);
        assertThat(fullPage).hasSize(14);

        assertThat(fullPageStatementCount)
                .as("statement count must not grow with page size (today's N+1 gives ~12 for size=11)")
                .isEqualTo(smallPageStatementCount)
                .isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("AC-333-N1-2: pagination/dedup integrity - full page has exactly 14 distinct rows, multi-opening posts appear once")
    void fullPageHasNoCartesianDuplicatesFromLocationsFetch() {
        given()
                .queryParam("size", 14)
                .queryParam("sort", "oldest")
                .when().get(JOBS)
                .then()
                .statusCode(200)
                .body("content.size()", equalTo(14))
                .body("totalElements", equalTo(14))
                .body("content.findAll { it.id == '" + ROW_8_ID + "' }.size()", equalTo(1))
                .body("content.findAll { it.id == '" + ROW_9_ID + "' }.size()", equalTo(1))
                .body("content.findAll { it.id == '" + ROW_10_ID + "' }.size()", equalTo(1));

        List<String> ids = given()
                .queryParam("size", 14)
                .queryParam("sort", "oldest")
                .when().get(JOBS)
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("content.id", String.class);

        assertThat(ids).hasSize(14);
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("AC-333-N1-3: list-level locations[] stays primary-first for multi-opening rows 8/9/10")
    void listLevelLocationsStaysPrimaryFirstForMultiOpeningRows() {
        var response = given()
                .queryParam("size", 14)
                .queryParam("sort", "oldest")
                .when().get(JOBS)
                .then()
                .statusCode(200)
                .extract().jsonPath();

        assertRowLocations(response, ROW_8_ID, "Barcelona", "Spain", "Amsterdam", "Netherlands");
        assertRowLocations(response, ROW_9_ID, "Barcelona", "Spain", "Madrid", "Spain");
        assertRowLocations(response, ROW_10_ID, "Berlin", "Germany", null, "Remote");
    }

    private void assertRowLocations(io.restassured.path.json.JsonPath response, UUID rowId,
            String primaryCity, String primaryCountry, String secondaryCity, String secondaryCountry) {
        String base = "content.find { it.id == '" + rowId + "' }";

        assertThat(response.getInt(base + ".locations.size()")).isEqualTo(2);
        assertThat(response.getBoolean(base + ".locations[0].primary")).isTrue();
        assertThat(response.getString(base + ".locations[0].city")).isEqualTo(primaryCity);
        assertThat(response.getString(base + ".locations[0].country")).isEqualTo(primaryCountry);
        assertThat(response.getBoolean(base + ".locations[1].primary")).isFalse();
        assertThat(response.getString(base + ".locations[1].city")).isEqualTo(secondaryCity);
        assertThat(response.getString(base + ".locations[1].country")).isEqualTo(secondaryCountry);
    }

    @Test
    @DisplayName("AC-333-N1-4: zero-child-row post (row 11) returns locations: [] in the LIST, not just the detail endpoint")
    void zeroChildRowPostReturnsEmptyLocationsArrayInList() {
        given()
                .queryParam("keyword", "Freight")
                .when().get(JOBS)
                .then()
                .statusCode(200)
                .body("content.size()", equalTo(1))
                .body("content[0].id", equalTo(ROW_11_ID.toString()))
                .body("content[0].locations", org.hamcrest.Matchers.empty())
                .body("content[0].location", org.hamcrest.Matchers.nullValue());
    }
}
