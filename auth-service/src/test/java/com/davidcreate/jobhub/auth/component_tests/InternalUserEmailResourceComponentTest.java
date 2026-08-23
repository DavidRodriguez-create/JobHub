package com.davidcreate.jobhub.auth.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Component tests for {@code GET /internal/users/emails} (ticket #100, ADR 0008).
 *
 * Protected by the {@code X-Service-Key} header (pre-shared API key), not a user JWT.
 * Seed users live in {@code db/test-seeds.sql}:
 * <ul>
 *   <li>{@code f0000000-...-0001} — verified</li>
 *   <li>{@code f0000000-...-0002} — verified</li>
 *   <li>{@code f0000000-...-0003} — unverified</li>
 *   <li>{@code f0000000-...-0099} — does not exist</li>
 * </ul>
 */
@QuarkusTest
@DisplayName("Internal User Email Resource Component Tests")
class InternalUserEmailResourceComponentTest {

    private static final String BASE = "/internal/users/emails";
    private static final String SERVICE_KEY_HEADER = "X-Service-Key";
    private static final String VALID_SERVICE_KEY = "test-internal-key";

    private static final String VERIFIED_1 = "f0000000-0000-0000-0000-000000000001";
    private static final String VERIFIED_2 = "f0000000-0000-0000-0000-000000000002";
    private static final String UNVERIFIED = "f0000000-0000-0000-0000-000000000003";
    private static final String NON_EXISTENT = "f0000000-0000-0000-0000-000000000099";

    @Test
    @DisplayName("✓ returns only verified, existing users — exact-count assertion (TC-31)")
    void batchEmailLookupReturnsOnlyVerifiedExistingUsers() {
        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .queryParam("userIds", VERIFIED_1, VERIFIED_2, UNVERIFIED, NON_EXISTENT)
                .when().get(BASE)
                .then()
                .statusCode(200)
                .body("emails", hasSize(2))
                .body("emails.find { it.userId == '" + VERIFIED_1 + "' }.email", org.hamcrest.Matchers.equalTo("verified1@example.com"))
                .body("emails.find { it.userId == '" + VERIFIED_2 + "' }.email", org.hamcrest.Matchers.equalTo("verified2@example.com"))
                .body("emails.find { it.userId == '" + UNVERIFIED + "' }", org.hamcrest.Matchers.nullValue())
                .body("emails.find { it.userId == '" + NON_EXISTENT + "' }", org.hamcrest.Matchers.nullValue());
    }

    @Test
    @DisplayName("✓ single existing verified user → exactly 1 entry (TC-39)")
    void batchEmailLookupWithSingleExistingUserId() {
        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .queryParam("userIds", VERIFIED_1)
                .when().get(BASE)
                .then()
                .statusCode(200)
                .body("emails", hasSize(1))
                .body("emails[0].userId", org.hamcrest.Matchers.equalTo(VERIFIED_1))
                .body("emails[0].email", org.hamcrest.Matchers.equalTo("verified1@example.com"));
    }

    @Test
    @DisplayName("✗ missing userIds query param → 400 (TC-40)")
    void batchEmailLookupMissingUserIdsParamReturns400() {
        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .when().get(BASE)
                .then()
                .statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    @Test
    @DisplayName("✗ invalid UUID format → 400 (TC-41)")
    void batchEmailLookupInvalidUuidFormatReturns400() {
        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .queryParam("userIds", "not-a-uuid")
                .when().get(BASE)
                .then()
                .statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    @Test
    @DisplayName("✗ missing X-Service-Key header → 401 (TC-42)")
    void batchEmailLookupMissingServiceKeyReturns401() {
        given()
                .queryParam("userIds", VERIFIED_1)
                .when().get(BASE)
                .then()
                .statusCode(401)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    @Test
    @DisplayName("✗ wrong X-Service-Key header → 401 (TC-43)")
    void batchEmailLookupWrongServiceKeyReturns401() {
        given()
                .header(SERVICE_KEY_HEADER, "wrong-value")
                .queryParam("userIds", VERIFIED_1)
                .when().get(BASE)
                .then()
                .statusCode(401)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    @Test
    @DisplayName("✓ only unverified user requested → empty result, not an error (TC-34)")
    void batchEmailLookupEmptyResultWhenOnlyUnverifiedUserRequested() {
        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .queryParam("userIds", UNVERIFIED)
                .when().get(BASE)
                .then()
                .statusCode(200)
                .body("emails", empty());
    }

    @Test
    @DisplayName("✓ only non-existent user requested → empty result, not an error (TC-36)")
    void batchEmailLookupEmptyResultWhenOnlyNonexistentUserRequested() {
        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .queryParam("userIds", NON_EXISTENT)
                .when().get(BASE)
                .then()
                .statusCode(200)
                .body("emails", empty());
    }
}
