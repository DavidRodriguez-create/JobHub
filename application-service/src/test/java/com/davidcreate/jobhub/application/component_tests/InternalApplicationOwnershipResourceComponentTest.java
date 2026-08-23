package com.davidcreate.jobhub.application.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * Component tests for HEAD /internal/applications/{id}/owner/{userId}.
 *
 * OWN-C-001: 204 when application is owned by the given user
 * OWN-C-002: 404 when application exists but belongs to a different user
 * OWN-C-003: 404 when application does not exist at all
 * OWN-C-004: 401 when X-Service-Key header is missing
 * OWN-C-005: 401 when X-Service-Key is wrong
 *
 * OWN-C-010 (repository crash -> 500) lives in
 * {@link InternalApplicationOwnershipResourceFailureComponentTest} (separate @QuarkusTest to use @InjectMock).
 *
 * No @TestHTTPEndpoint per CLAUDE.md rule -- use constant BASE path.
 */
@QuarkusTest
@DisplayName("Internal Application Ownership Resource Component Tests")
class InternalApplicationOwnershipResourceComponentTest {

    private static final String BASE = "/internal/applications";
    private static final String SERVICE_KEY = "test-internal-key";
    private static final String WRONG_KEY = "wrong-key";

    // Seed: f0000000-0000-0000-0000-000000000099 owned by fa000000-0000-0000-0000-000000000001
    private static final String OWNED_APP_ID  = "f0000000-0000-0000-0000-000000000099";
    private static final String OWNER_USER_ID = "fa000000-0000-0000-0000-000000000001";
    private static final String OTHER_USER_ID = "fa000000-0000-0000-0000-000000000002";

    // ── OWN-C-001 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("OWN-C-001: 204 when application is owned by the given user")
    void head_ownedByUser_returns204() {
        given()
            .header("X-Service-Key", SERVICE_KEY)
        .when()
            .head(BASE + "/" + OWNED_APP_ID + "/owner/" + OWNER_USER_ID)
        .then()
            .statusCode(204);
    }

    // ── OWN-C-002 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("OWN-C-002: 404 when application exists but belongs to a different user")
    void head_appExistsButDifferentUser_returns404() {
        given()
            .header("X-Service-Key", SERVICE_KEY)
        .when()
            .head(BASE + "/" + OWNED_APP_ID + "/owner/" + OTHER_USER_ID)
        .then()
            .statusCode(404);
    }

    // ── OWN-C-003 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("OWN-C-003: 404 when application does not exist at all")
    void head_unknownApplicationId_returns404() {
        given()
            .header("X-Service-Key", SERVICE_KEY)
        .when()
            .head(BASE + "/" + UUID.randomUUID() + "/owner/" + OWNER_USER_ID)
        .then()
            .statusCode(404);
    }

    // ── OWN-C-004 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("OWN-C-004: 401 when X-Service-Key header is missing")
    void head_missingServiceKey_returns401() {
        given()
        .when()
            .head(BASE + "/" + OWNED_APP_ID + "/owner/" + OWNER_USER_ID)
        .then()
            .statusCode(401);
    }

    // ── OWN-C-005 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("OWN-C-005: 401 when X-Service-Key is wrong")
    void head_wrongServiceKey_returns401() {
        given()
            .header("X-Service-Key", WRONG_KEY)
        .when()
            .head(BASE + "/" + OWNED_APP_ID + "/owner/" + OWNER_USER_ID)
        .then()
            .statusCode(401);
    }
}
