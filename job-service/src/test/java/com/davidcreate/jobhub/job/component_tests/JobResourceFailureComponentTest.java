package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.CacheDisabledProfile;
import com.davidcreate.jobhub.job.domain.model.FacetValue;
import com.davidcreate.jobhub.job.domain.model.JobFacets;
import com.davidcreate.jobhub.job.domain.port.out.JobPostRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Failure-path component tests for {@code JobResource}.
 *
 * <p>Lives in its own class because {@code @InjectMock} replaces the bean for
 * the whole {@code @QuarkusTest} class; we don't want to lose the real
 * DevServices-backed repository in {@link JobResourceComponentTest}.
 *
 * <p>{@code @TestProfile(CacheDisabledProfile.class)} (Story #331 / ADR 0018):
 * without it, this class shares the default-profile Quarkus instance (and its
 * singleton {@code CountCache}) with {@link JobResourceComponentTest}, so a
 * count cached by an earlier, successful {@code GET /jobs} there could mask a
 * repository failure injected here.
 */
@QuarkusTest
@TestProfile(CacheDisabledProfile.class)
@DisplayName("Job Resource Failure Component Tests")
class JobResourceFailureComponentTest {

    private static final String JOBS = "/jobs";

    @InjectMock
    JobPostRepository jobPostRepository;

    @Test
    @DisplayName("✗ search query timeout or DB connection crash → 500 internal server error")
    void testSearchServerError() {
        when(jobPostRepository.search(any()))
                .thenThrow(new RuntimeException("Simulated DB connection crash"));

        given()
                .when().get(JOBS)
                .then()
                .statusCode(500);
    }

    @Test
    @DisplayName("✗ data corruption or fetch exception → 500 internal server error")
    void testGetJobServerError() {
        when(jobPostRepository.findJobById(any()))
                .thenThrow(new RuntimeException("Simulated data corruption"));

        given()
                .pathParam("id", UUID.randomUUID())
                .when().get(JOBS + "/{id}")
                .then()
                .statusCode(500);
    }

    // BE-F16 / FC332-CF-01 (Story #332 regression: must remain green under the facet caching wiring)
    @Test
    @DisplayName("✗ facets DB crash → 500 with {error, message} body")
    void testFacetsServerError() {
        when(jobPostRepository.facets(any()))
                .thenThrow(new RuntimeException("Simulated facets DB crash"));

        given()
                .when().get(JOBS + "/facets")
                .then()
                .statusCode(500)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // FC332-CF-02 (Story #332 / ADR 0020): a failed compute is never cached as a false
    // "hit" on retry: caching must not swallow or mask a repository failure.
    @Test
    @DisplayName("FC332-CF-02: facets() still throwing, called twice → both calls 500 (never cached as a false hit)")
    void testFacetsServerErrorNeverCachedAsHitOnRetry() {
        when(jobPostRepository.facets(any()))
                .thenThrow(new RuntimeException("Simulated facets DB crash"));

        given().when().get(JOBS + "/facets").then().statusCode(500);
        given().when().get(JOBS + "/facets").then().statusCode(500);
    }

    // FC332-CF-03 (Story #332 / ADR 0020): CrawlGenerationStamp is fail-soft, a stamp-read
    // DB error must never cascade into an endpoint-level failure (FC332-U-21/22 at the unit
    // layer; this is the component-level companion, decisive alongside FC332-C-14).
    @Test
    @DisplayName("FC332-CF-03: facetDataVersion() throws (stamp-read DB error) → 200, body matches the stubbed facets exactly")
    void testStampReadFailureDoesNotCascadeIntoEndpointFailure() {
        JobFacets expected = new JobFacets(
                List.of(new FacetValue("Stripe", 4L)),
                List.of(new FacetValue("Spain", 5L)),
                List.of(new FacetValue("English", 6L)),
                List.of(new FacetValue("full-time", 5L)),
                List.of(new FacetValue("senior", 3L)),
                60000, 110000);
        when(jobPostRepository.facetDataVersion())
                .thenThrow(new RuntimeException("Simulated stamp DB error"));
        when(jobPostRepository.facets(any())).thenReturn(expected);

        given()
                .when().get(JOBS + "/facets")
                .then()
                .statusCode(200)
                .body("companies[0].value", equalTo("Stripe"))
                .body("companies[0].count", equalTo(4))
                .body("compensationMin", equalTo(60000))
                .body("compensationMax", equalTo(110000));
    }

    // FC332-CF-04: robustness extension of FC332-CF-03; repeated stamp-read failures must
    // never crash-loop; every repeated call still succeeds.
    @Test
    @DisplayName("FC332-CF-04: facetDataVersion() throws on every call, repeated GET calls → all still 200 (no crash-loop)")
    void testRepeatedStampReadFailuresNeverCascade() {
        when(jobPostRepository.facetDataVersion())
                .thenThrow(new RuntimeException("Simulated stamp DB error"));
        when(jobPostRepository.facets(any())).thenReturn(new JobFacets(
                List.of(), List.of(), List.of(), List.of(), List.of(), null, null));

        for (int i = 0; i < 3; i++) {
            given().when().get(JOBS + "/facets").then().statusCode(200);
        }
    }

    // Story #331 / ADR 0018, TC-331-34: the planner-estimate step is a NEW
    // repository method (estimateCount), distinct from count()/search(): a real
    // repository could have the estimate step throw while count/search stay
    // reachable, or vice versa, so it is mocked separately here.
    @Test
    @DisplayName("TC-331-34: planner-estimate step throws → 500 with {error, message} body (same contract as BE-F16)")
    void testEstimateCountServerError() {
        when(jobPostRepository.estimateCount(any()))
                .thenThrow(new RuntimeException("Simulated planner EXPLAIN failure"));

        given()
                .when().get(JOBS)
                .then()
                .statusCode(500)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // TC-331-35: the cache (CountCache) is a plain, non-injected internal component
    // behind JobService, not a port, no @InjectMock seam. Not applicable per the
    // QAE doc's own escalation note; recorded here rather than silently dropped.
}
