package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.CountExactModeProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Story #331 / ADR 0018: {@code job.search.count.mode=exact} with an
 * adversarially-low {@code exact-threshold=0} (which would force every query
 * onto the estimate branch under {@code hybrid}) proves {@code mode=exact} fully
 * restores the legacy always-exact behaviour, ignoring the threshold entirely
 * (AC-331-8).
 */
@QuarkusTest
@TestProfile(CountExactModeProfile.class)
@DisplayName("Job Resource Component Tests (mode=exact, exact-threshold=0)")
class JobResourceCountExactModeComponentTest {

    private static final String JOBS = "/jobs";

    @Test
    @DisplayName("TC-331-26 (AC-331-8): no filters, 14 real rows -> totalElements==14 exactly, countIsEstimate absent/false")
    void modeExactIgnoresAdversariallyLowThreshold() {
        given()
                .when().get(JOBS)
                .then()
                .statusCode(200)
                .body("totalElements", equalTo(14))
                .body("countIsEstimate", anyOf(nullValue(), is(false)));
    }
}
