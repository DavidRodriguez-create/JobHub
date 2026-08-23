package com.davidcreate.jobhub.crawler.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

@QuarkusTest
@DisplayName("Health endpoint Component Tests")
class HealthComponentTest {

    private static final String HEALTH = "/q/health";

    @Test
    @DisplayName("GET /q/health reports UP and is reachable without authentication")
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
    @DisplayName("GET /q/health/live reports UP")
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
    @DisplayName("GET /q/health/ready reports UP and includes the datasource check, so readiness tracks the database")
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
}
