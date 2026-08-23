package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.EmptyJobPostProfile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;

/**
 * Component test for AC-332-15: an empty/pre-crawl {@code crawler.job_post} table does
 * not break the generation stamp or the endpoint. Runs in its own
 * {@link EmptyJobPostProfile} (fresh drop-and-create DB, facet cache enabled with
 * default TTLs); the zero-row state is a committed DELETE run before each test via
 * {@link QuarkusTransaction#requiringNew()}, mirroring
 * {@code AdminTriggerDedupeStatesComponentTest}'s established pattern.
 */
@QuarkusTest
@TestProfile(EmptyJobPostProfile.class)
@DisplayName("GET /jobs/facets: empty crawler.job_post table (FC332-C-10)")
class JobFacetsEmptyTableComponentTest {

    private static final String FACETS = "/jobs/facets";

    @Inject
    EntityManager entityManager;

    @BeforeEach
    void emptyTheTable() {
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createQuery("delete from JobPostLocationEntity").executeUpdate();
            entityManager.createQuery("delete from JobPostEntity").executeUpdate();
        });
    }

    @Test
    @DisplayName("FC332-C-10 (AC-332-15): no rows, no filters, called twice -> 200, every group empty, comp bounds null, identical both times")
    void emptyTableReturnsEmptyFacetsBothCalls() {
        given().when().get(FACETS)
                .then().statusCode(200)
                .body("companies", empty())
                .body("locations", empty())
                .body("languages", empty())
                .body("employmentTypes", empty())
                .body("careerLevels", empty())
                .body("compensationMin", nullValue())
                .body("compensationMax", nullValue());

        given().when().get(FACETS)
                .then().statusCode(200)
                .body("companies", empty())
                .body("locations", empty())
                .body("languages", empty())
                .body("employmentTypes", empty())
                .body("careerLevels", empty())
                .body("compensationMin", nullValue())
                .body("compensationMax", nullValue());
    }
}
