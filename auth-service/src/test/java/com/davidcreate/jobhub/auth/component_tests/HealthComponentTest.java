package com.davidcreate.jobhub.auth.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

/**
 * auth-service is root-pathed at /auth (quarkus.http.root-path), and its
 * non-application root follows it (quarkus.http.non-application-root-path=/auth/q),
 * so on the wire health lives at /auth/q/health, NOT at /q/health. Anything probing
 * this service from outside, compose healthchecks included, must use the prefix.
 *
 * Inside a @QuarkusTest the paths below look unprefixed because Quarkus configures
 * RestAssured with basePath = quarkus.http.root-path and prepends /auth for us.
 * "/q/health" here is "/auth/q/health" on the wire. Writing the prefix out in full
 * would double it and 404. The last test pins the real, unprefixed path by clearing
 * basePath explicitly.
 */
@QuarkusTest
@DisplayName("Health endpoint Component Tests")
class HealthComponentTest {

    private static final String HEALTH = "/q/health";

    @Test
    @DisplayName("GET /auth/q/health reports UP and is reachable without authentication")
    void aggregateHealthIsUpAndUnauthenticated() {
        given()
        .when()
            .get(HEALTH)
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("GET /auth/q/health/live reports UP")
    void livenessIsUp() {
        given()
        .when()
            .get(HEALTH + "/live")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("GET /auth/q/health/ready reports UP and includes the datasource check, so readiness tracks the database")
    void readinessIsUpAndCoversTheDatasource() {
        given()
        .when()
            .get(HEALTH + "/ready")
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .body("status", equalTo("UP"))
            .body("checks.name", hasItem(containsString("Database")));
    }

    @Test
    @DisplayName("the root-path-less /q/health is NOT served, so a probe configured without the /auth prefix fails loudly instead of silently")
    void unprefixedHealthIsNotServed() {
        given()
            .basePath("")
        .when()
            .get("/q/health")
        .then()
            .log().ifValidationFails()
            .statusCode(404);
    }
}
