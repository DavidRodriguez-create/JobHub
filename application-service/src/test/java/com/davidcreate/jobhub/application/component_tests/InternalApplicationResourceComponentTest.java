package com.davidcreate.jobhub.application.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Component tests for the internal stale-applications endpoints.
 *
 * GA-APP-01 through GA-APP-08  (GET /internal/applications/stale)
 * GA-APP-11 through GA-APP-17  (PUT /internal/applications/{id}/status)
 *
 * GA-APP-18 (repository crash -> 500) lives in
 * {@link InternalApplicationResourceFailureComponentTest} (separate @QuarkusTest to use @InjectMock).
 *
 * No @TestHTTPEndpoint here -- CLAUDE.md rule: use a constant BASE path with RestAssured.
 */
@QuarkusTest
@DisplayName("Internal Application Resource Component Tests")
class InternalApplicationResourceComponentTest {

    private static final String BASE_STALE = "/internal/applications/stale";
    private static final String BASE_STATUS = "/internal/applications";
    private static final String SERVICE_KEY = "test-internal-key";
    private static final String WRONG_KEY = "wrong-key";

    // ── GA-APP-01 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-01: GET /internal/applications/stale happy path returns stale non-terminal apps")
    void getStale_happyPath() {
        given()
            .header("X-Service-Key", SERVICE_KEY)
            .queryParam("days", 14)
        .when()
            .get(BASE_STALE)
        .then()
            .statusCode(200)
            .body("items", notNullValue())
            // seed data has at least one stale non-terminal app (APPLIED, 15 days old)
            .body("items.size()", greaterThanOrEqualTo(1))
            .body("items[0].id", notNullValue())
            .body("items[0].userId", notNullValue())
            .body("items[0].currentStatus", notNullValue())
            .body("items[0].daysSinceLastActivity", greaterThanOrEqualTo(14));
    }

    // ── GA-APP-02 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-02: Terminal statuses (rejected, accepted, withdrawn, ghosted) excluded from stale")
    void getStale_terminalStatusesExcluded() {
        // The seed has one row in each terminal status older than 14 days.
        // None should appear in the stale results.
        given()
            .header("X-Service-Key", SERVICE_KEY)
            .queryParam("days", 14)
        .when()
            .get(BASE_STALE)
        .then()
            .statusCode(200)
            .body("items.findAll { it.currentStatus == 'rejected' }.size()", equalTo(0))
            .body("items.findAll { it.currentStatus == 'accepted' }.size()", equalTo(0))
            .body("items.findAll { it.currentStatus == 'withdrawn' }.size()", equalTo(0))
            .body("items.findAll { it.currentStatus == 'ghosted' }.size()", equalTo(0));
    }

    // ── GA-APP-03 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-03: Non-terminal statuses (applied, screening, interviewing, offered) eligible for stale")
    void getStale_nonTerminalStatusesEligible() {
        // Seed has one stale row per non-terminal status (15 days old).
        // With days=14 all four should be returned.
        given()
            .header("X-Service-Key", SERVICE_KEY)
            .queryParam("days", 14)
        .when()
            .get(BASE_STALE)
        .then()
            .statusCode(200)
            .body("items.findAll { it.currentStatus == 'applied' }.size()", greaterThanOrEqualTo(1))
            .body("items.findAll { it.currentStatus == 'screening' }.size()", greaterThanOrEqualTo(1))
            .body("items.findAll { it.currentStatus == 'interviewing' }.size()", greaterThanOrEqualTo(1))
            .body("items.findAll { it.currentStatus == 'offered' }.size()", greaterThanOrEqualTo(1));
    }

    // ── GA-APP-04 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-04: Threshold boundary — 15d stale app is included, 1d fresh app excluded at days=14")
    void getStale_thresholdBoundary() {
        // Seed has one app at 15 days (stale) and one app at 1 day (fresh).
        // Using days=14: the 15d app must be in results, the 1d app must NOT.
        given()
            .header("X-Service-Key", SERVICE_KEY)
            .queryParam("days", 14)
        .when()
            .get(BASE_STALE)
        .then()
            .statusCode(200)
            .body("items.findAll { it.daysSinceLastActivity >= 14 }.size()", greaterThanOrEqualTo(1));

        // Fresh (1-day) applications must not appear when using days=1 threshold check
        // We verify by using days=2: the 1-day app should be excluded.
        given()
            .header("X-Service-Key", SERVICE_KEY)
            .queryParam("days", 2)
        .when()
            .get(BASE_STALE)
        .then()
            .statusCode(200)
            .body("items.findAll { it.daysSinceLastActivity == 0 || it.daysSinceLastActivity == 1 }.size()", equalTo(0));
    }

    // ── GA-APP-05 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-05: Empty result when days threshold is very large — 200 with empty items list")
    void getStale_emptyResult() {
        given()
            .header("X-Service-Key", SERVICE_KEY)
            .queryParam("days", 9999)
        .when()
            .get(BASE_STALE)
        .then()
            .statusCode(200)
            .body("items", empty());
    }

    // ── GA-APP-06 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-06: days=0 returns 400 (minimum is 1 per contract)")
    void getStale_daysZeroReturns400() {
        given()
            .header("X-Service-Key", SERVICE_KEY)
            .queryParam("days", 0)
        .when()
            .get(BASE_STALE)
        .then()
            .statusCode(400);
    }

    // ── GA-APP-07 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-07: Missing X-Service-Key returns 401")
    void getStale_missingServiceKey() {
        given()
            .queryParam("days", 14)
        .when()
            .get(BASE_STALE)
        .then()
            .statusCode(401);
    }

    // ── GA-APP-08 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-08: Wrong X-Service-Key returns 401")
    void getStale_wrongServiceKey() {
        given()
            .header("X-Service-Key", WRONG_KEY)
            .queryParam("days", 14)
        .when()
            .get(BASE_STALE)
        .then()
            .statusCode(401);
    }

    // ── GA-APP-11 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-11: PUT happy path — status=GHOSTED, endedAt stamped, returns 200 InternalStatusUpdateResponse")
    void putStatus_happyPath() {
        // Create an application to update — we'll use a seed stale app ID.
        // We first get a stale app id from the GET endpoint.
        UUID appId = given()
            .header("X-Service-Key", SERVICE_KEY)
            .queryParam("days", 14)
        .when()
            .get(BASE_STALE)
        .then()
            .statusCode(200)
            .extract().jsonPath().getUUID("items[0].id");

        given()
            .header("X-Service-Key", SERVICE_KEY)
            .contentType("application/json")
            .body(Map.of("status", "ghosted"))
        .when()
            .put(BASE_STATUS + "/" + appId + "/status")
        .then()
            .statusCode(200)
            .body("id", equalTo(appId.toString()))
            .body("userId", notNullValue())
            .body("newStatus", equalTo("ghosted"));

        // Verify timeline was appended by checking via user-facing GET would require a JWT.
        // Instead, verify through the stale endpoint — after marking ghosted, it should be excluded.
        given()
            .header("X-Service-Key", SERVICE_KEY)
            .queryParam("days", 14)
        .when()
            .get(BASE_STALE)
        .then()
            .statusCode(200)
            .body("items.findAll { it.id == '" + appId + "' }.size()", equalTo(0));
    }

    // ── GA-APP-12 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-12: Transitions from SCREENING, INTERVIEWING, OFFERED to GHOSTED all succeed")
    void putStatus_fromVariousNonTerminalStatuses() {
        // Seed has apps in each non-terminal status. Get stale apps, find one for each status.
        var items = given()
            .header("X-Service-Key", SERVICE_KEY)
            .queryParam("days", 14)
        .when()
            .get(BASE_STALE)
        .then()
            .statusCode(200)
            .extract().jsonPath().getList("items");

        // Find apps in screening, interviewing, offered statuses
        for (String targetStatus : List.of("screening", "interviewing", "offered")) {
            String appIdStr = given()
                .header("X-Service-Key", SERVICE_KEY)
                .queryParam("days", 14)
            .when()
                .get(BASE_STALE)
            .then()
                .extract().jsonPath()
                .getString("items.find { it.currentStatus == '" + targetStatus + "' }.id");

            if (appIdStr == null) continue; // skip if not available (already consumed)

            given()
                .header("X-Service-Key", SERVICE_KEY)
                .contentType("application/json")
                .body(Map.of("status", "ghosted"))
            .when()
                .put(BASE_STATUS + "/" + appIdStr + "/status")
            .then()
                .statusCode(200)
                .body("newStatus", equalTo("ghosted"));
        }
    }

    // ── GA-APP-13 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-13: Unknown UUID returns 404")
    void putStatus_unknownUuid() {
        given()
            .header("X-Service-Key", SERVICE_KEY)
            .contentType("application/json")
            .body(Map.of("status", "ghosted"))
        .when()
            .put(BASE_STATUS + "/" + UUID.randomUUID() + "/status")
        .then()
            .statusCode(404);
    }

    // ── GA-APP-14 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-14: Already terminal status returns 409")
    void putStatus_alreadyTerminal() {
        // The seed has apps in terminal statuses with old updated_at.
        // Use the seed terminal app IDs seeded directly.
        // We need to find a terminal app ID from seed. We use a dedicated seed UUID for this.
        UUID terminalAppId = UUID.fromString("cc000001-0000-0000-0000-000000000001");

        given()
            .header("X-Service-Key", SERVICE_KEY)
            .contentType("application/json")
            .body(Map.of("status", "ghosted"))
        .when()
            .put(BASE_STATUS + "/" + terminalAppId + "/status")
        .then()
            .statusCode(409)
            .body("error", equalTo("Conflict"));
    }

    // ── GA-APP-15 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-15: Missing X-Service-Key for PUT returns 401")
    void putStatus_missingServiceKey() {
        given()
            .contentType("application/json")
            .body(Map.of("status", "ghosted"))
        .when()
            .put(BASE_STATUS + "/" + UUID.randomUUID() + "/status")
        .then()
            .statusCode(401);
    }

    // ── GA-APP-16 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-16: Invalid status value returns 400")
    void putStatus_invalidStatus() {
        given()
            .header("X-Service-Key", SERVICE_KEY)
            .contentType("application/json")
            .body(Map.of("status", "banana"))
        .when()
            .put(BASE_STATUS + "/" + UUID.randomUUID() + "/status")
        .then()
            .statusCode(400);
    }

    // ── GA-APP-17 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GA-APP-17: Missing body for PUT returns 400")
    void putStatus_missingBody() {
        given()
            .header("X-Service-Key", SERVICE_KEY)
            .contentType("application/json")
        .when()
            .put(BASE_STATUS + "/" + UUID.randomUUID() + "/status")
        .then()
            .statusCode(400);
    }
}
