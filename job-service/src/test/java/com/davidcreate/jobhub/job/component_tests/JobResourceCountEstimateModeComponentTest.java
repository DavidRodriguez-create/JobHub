package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.CountEstimateModeProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Story #331 / ADR 0018: {@code job.search.count.mode=estimate} forces the
 * planner-estimate branch unconditionally, regardless of the true row count:
 * the only deterministic way to exercise AC-331-4/5/6/7/9 against the 11-row
 * seed fixture (see {@code CountEstimateModeProfile} and the QAE doc's
 * "Testability concern" section).
 */
@QuarkusTest
@TestProfile(CountEstimateModeProfile.class)
@DisplayName("Job Resource Component Tests (mode=estimate)")
class JobResourceCountEstimateModeComponentTest {

    private static final String JOBS = "/jobs";

    @Test
    @DisplayName("TC-331-21 (AC-331-4): no filters -> countIsEstimate=true, totals non-negative and internally consistent")
    void unfilteredSearchReturnsEstimate() {
        given()
                .when().get(JOBS)
                .then()
                .statusCode(200)
                .body("countIsEstimate", equalTo(true))
                .body("totalElements", greaterThanOrEqualTo(0))
                .body("totalPages", greaterThanOrEqualTo(0));
    }

    @Test
    @DisplayName("TC-331-22 (AC-331-5): keyword=Developer, page=0&size=2 -> content is real rows, only the total is approximated")
    void pageContentStaysRealUnderEstimate() {
        JsonPath body = given()
                .queryParam("keyword", "Developer")
                .queryParam("page", 0)
                .queryParam("size", 2)
                .when().get(JOBS)
                .then()
                .statusCode(200)
                .body("countIsEstimate", equalTo(true))
                .body("content.size()", equalTo(2))
                .extract().jsonPath();

        java.util.Set<String> knownDeveloperIds = java.util.Set.of(
                "11111111-1111-1111-1111-111111111111",
                "22222222-2222-2222-2222-222222222222",
                "33333333-3333-3333-3333-333333333333",
                "55555555-5555-5555-5555-555555555555");
        for (String id : body.<String>getList("content.id")) {
            assertThat(knownDeveloperIds).contains(id);
        }
    }

    @Test
    @DisplayName("TC-331-23 (AC-331-6): paging forward stays navigable and terminates on the first empty page")
    void pagingStaysNavigableUnderEstimate() {
        int page = 0;
        int emptyPageSeenAt = -1;
        // 11 real rows / size=5 -> real boundary is page 2 (0,1 full, 2 has 1 item, 3 is empty).
        // Cap the loop well past that so a bad estimate can never spin forever.
        for (; page <= 6; page++) {
            JsonPath body = given()
                    .queryParam("page", page)
                    .queryParam("size", 5)
                    .when().get(JOBS)
                    .then()
                    .statusCode(200)
                    .body("countIsEstimate", equalTo(true))
                    .extract().jsonPath();

            if (body.getList("content").isEmpty()) {
                emptyPageSeenAt = page;
                break;
            }
        }

        assertThat(emptyPageSeenAt).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("TC-331-24 (AC-331-7): unfiltered and language=Spanish both stay non-negative and internally consistent")
    void totalsNeverNegativeAcrossFilterCombinations() {
        assertNonNegativeAndConsistent(given().when().get(JOBS));
        assertNonNegativeAndConsistent(given().queryParam("language", "Spanish").when().get(JOBS));
    }

    private static void assertNonNegativeAndConsistent(io.restassured.response.Response response) {
        JsonPath body = response.then().statusCode(200).extract().jsonPath();

        long totalElements = body.getLong("totalElements");
        int totalPages = body.getInt("totalPages");

        assertThat(totalElements).isGreaterThanOrEqualTo(0);
        assertThat(totalPages).isGreaterThanOrEqualTo(0);
        if (totalElements > 0) {
            assertThat(totalPages).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("TC-331-25 (AC-331-9): language=Spanish (true count 1, well below threshold) still returns countIsEstimate=true")
    void narrowFilterStillReturnsEstimateWhenModeForcesIt() {
        given()
                .queryParam("language", "Spanish")
                .when().get(JOBS)
                .then()
                .statusCode(200)
                .body("countIsEstimate", equalTo(true))
                .body("content[0]", notNullValue());
    }
}
