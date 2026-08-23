package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.EmptyJobPostCacheDisabledProfile;
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
 * Component test for AC-332-15/18: same empty-table setup as
 * {@link JobFacetsEmptyTableComponentTest} but with the facet cache disabled, own fresh
 * drop-and-create DB via {@link EmptyJobPostCacheDisabledProfile}.
 */
@QuarkusTest
@TestProfile(EmptyJobPostCacheDisabledProfile.class)
@DisplayName("GET /jobs/facets: empty crawler.job_post table, cache disabled (FC332-C-11)")
class JobFacetsEmptyTableCacheDisabledComponentTest {

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
    @DisplayName("FC332-C-11 (AC-332-15/18): no rows, cache disabled, single call -> 200, every group empty, comp bounds null")
    void emptyTableWithCacheDisabledReturnsEmptyFacets() {
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
