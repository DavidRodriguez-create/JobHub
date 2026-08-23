package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.LowCountThresholdProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Story #331 / ADR 0018: {@code job.search.count.mode=hybrid} with
 * {@code exact-threshold=0}: a wiring smoke proving {@code exact-threshold} is
 * actually read from config at runtime and drives the hybrid branch (complements
 * the unit-level boundary proof in {@code CountDecisionTest}, TC-331-3/4).
 */
@QuarkusTest
@TestProfile(LowCountThresholdProfile.class)
@DisplayName("Job Resource Component Tests (mode=hybrid, exact-threshold=0)")
class JobResourceCountThresholdComponentTest {

    private static final String JOBS = "/jobs";

    @Test
    @DisplayName("TC-331-27 (AC-331-3/4 wiring smoke): no filters, threshold=0 -> planner estimate (>0) exceeds it, countIsEstimate=true")
    void lowThresholdForcesEstimateBranch() {
        given()
                .when().get(JOBS)
                .then()
                .statusCode(200)
                .body("countIsEstimate", equalTo(true));
    }
}
