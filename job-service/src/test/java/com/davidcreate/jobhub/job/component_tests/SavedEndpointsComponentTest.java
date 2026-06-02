package com.davidcreate.jobhub.job.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Component tests for the authenticated Saved Jobs and Saved Filters endpoints.
 * Each test authenticates as a distinct user (the {@code sub} claim) so the
 * shared DevServices database does not leak state between tests.
 */
@QuarkusTest
@DisplayName("Saved Jobs & Saved Filters Component Tests")
class SavedEndpointsComponentTest {

    private static final String SAVED_JOBS = "/jobs/saved";
    private static final String SAVED_FILTERS = "/jobs/filters/saved";
    // Seeded in db/test-seeds.sql.
    private static final String KNOWN_JOB_ID = "11111111-1111-1111-1111-111111111111";

    // ── Saved Jobs ────────────────────────────────────────────────────────────

    @Test
    @TestSecurity(user = "10000000-0000-0000-0000-000000000001", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "10000000-0000-0000-0000-000000000001"))
    @DisplayName("PUT bookmarks a job (idempotent); GET lists it with the embedded posting")
    void saveAndList() {
        given().when().put(SAVED_JOBS + "/" + KNOWN_JOB_ID).then().statusCode(204);
        // idempotent — saving again is still 204
        given().when().put(SAVED_JOBS + "/" + KNOWN_JOB_ID).then().statusCode(204);

        given().when().get(SAVED_JOBS)
                .then().statusCode(200)
                .body("totalElements", equalTo(1))
                .body("content[0].savedAt", notNullValue())
                .body("content[0].job.id", equalTo(KNOWN_JOB_ID))
                .body("content[0].job.company.name", equalTo("Stripe"));
    }

    @Test
    @TestSecurity(user = "10000000-0000-0000-0000-000000000002", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "10000000-0000-0000-0000-000000000002"))
    @DisplayName("PUT on a non-existent job → 404")
    void saveUnknownJob() {
        given().when().put(SAVED_JOBS + "/" + UUID.randomUUID()).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "10000000-0000-0000-0000-000000000003", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "10000000-0000-0000-0000-000000000003"))
    @DisplayName("DELETE removes a bookmark and is idempotent")
    void unsaveIdempotent() {
        given().when().put(SAVED_JOBS + "/" + KNOWN_JOB_ID).then().statusCode(204);
        given().when().delete(SAVED_JOBS + "/" + KNOWN_JOB_ID).then().statusCode(204);
        // already removed — still 204
        given().when().delete(SAVED_JOBS + "/" + KNOWN_JOB_ID).then().statusCode(204);

        given().when().get(SAVED_JOBS)
                .then().statusCode(200)
                .body("totalElements", equalTo(0));
    }

    @Test
    @TestSecurity(user = "10000000-0000-0000-0000-000000000004", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "10000000-0000-0000-0000-000000000004"))
    @DisplayName("GET /jobs/saved rejects size > 100 → 400")
    void listInvalidSize() {
        given().queryParam("size", 10_000).when().get(SAVED_JOBS).then().statusCode(400);
    }

    // ── Saved Filters ───────────────────────────────────────────────────────────

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000001", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000001"))
    @DisplayName("POST creates a preset; GET lists it; the filter values round-trip")
    void createAndList() {
        given().contentType("application/json")
                .body(Map.of("name", "Remote Java",
                        "filters", Map.of("keyword", "java", "location", java.util.List.of("Remote"))))
                .when().post(SAVED_FILTERS)
                .then().statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("Remote Java"))
                .body("filters.keyword", equalTo("java"))
                .body("filters.location[0]", equalTo("Remote"))
                .body("createdAt", notNullValue());

        given().when().get(SAVED_FILTERS)
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].name", equalTo("Remote Java"));
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000002", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000002"))
    @DisplayName("PATCH renames a preset")
    void renamePreset() {
        String id = given().contentType("application/json")
                .body(Map.of("name", "Old", "filters", Map.of("keyword", "go")))
                .when().post(SAVED_FILTERS).then().statusCode(201)
                .extract().jsonPath().getString("id");

        given().contentType("application/json")
                .body(Map.of("name", "New"))
                .when().patch(SAVED_FILTERS + "/" + id)
                .then().statusCode(200)
                .body("name", equalTo("New"))
                .body("filters.keyword", equalTo("go"));
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000003", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000003"))
    @DisplayName("PATCH with an empty body → 400")
    void emptyPatch() {
        String id = given().contentType("application/json")
                .body(Map.of("name", "X", "filters", Map.of()))
                .when().post(SAVED_FILTERS).then().statusCode(201)
                .extract().jsonPath().getString("id");

        given().contentType("application/json")
                .body(Map.of())
                .when().patch(SAVED_FILTERS + "/" + id)
                .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000004", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000004"))
    @DisplayName("DELETE removes a preset; deleting again → 404")
    void deletePreset() {
        String id = given().contentType("application/json")
                .body(Map.of("name", "Temp", "filters", Map.of()))
                .when().post(SAVED_FILTERS).then().statusCode(201)
                .extract().jsonPath().getString("id");

        given().when().delete(SAVED_FILTERS + "/" + id).then().statusCode(204);
        given().when().delete(SAVED_FILTERS + "/" + id).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000005", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000005"))
    @DisplayName("POST a 6th preset → 400 (limit of 5)")
    void presetLimit() {
        for (int i = 0; i < 5; i++) {
            given().contentType("application/json")
                    .body(Map.of("name", "P" + i, "filters", Map.of()))
                    .when().post(SAVED_FILTERS).then().statusCode(201);
        }
        given().contentType("application/json")
                .body(Map.of("name", "P6", "filters", Map.of()))
                .when().post(SAVED_FILTERS)
                .then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000006", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000006"))
    @DisplayName("POST with a name longer than 80 chars → 400")
    void nameTooLong() {
        given().contentType("application/json")
                .body(Map.of("name", "x".repeat(81), "filters", Map.of()))
                .when().post(SAVED_FILTERS)
                .then().statusCode(400);
    }
}
