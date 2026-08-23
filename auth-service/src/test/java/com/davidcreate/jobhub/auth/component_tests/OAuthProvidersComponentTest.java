package com.davidcreate.jobhub.auth.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * {@code GET /oauth/providers}, both-configured default test profile (ADR 0028,
 * Decision 2). No {@code @TestProfile} here - this is exactly today's default
 * {@code application.properties} (both providers configured), so it stays in the
 * shared component-tests fork (0/1-provider permutations live in the isolated
 * component-tests-profiles bucket, see {@code OAuthProvidersNoneConfiguredComponentTest}
 * et al.). Covers TC-506-B1..B4.
 */
@QuarkusTest
@DisplayName("OAuth Providers Availability Component Tests")
class OAuthProvidersComponentTest {

    private static final String PROVIDERS = "/oauth/providers";

    // TC-506-B1: AVAIL-BOOT-3/AVAIL-4.
    @Test
    @DisplayName("TC-506-B1: both providers configured -> 200, both available:true, google first")
    void bothProvidersConfiguredReturnsBothAvailable() {
        given().when().get(PROVIDERS)
                .then().statusCode(200)
                .body("providers[0].provider", equalTo("google"))
                .body("providers[0].available", equalTo(true))
                .body("providers[1].provider", equalTo("github"))
                .body("providers[1].available", equalTo(true));
    }

    // TC-506-B2: AVAIL-6.
    @Test
    @DisplayName("TC-506-B2: two calls in a row return the same stable order")
    void repeatedCallsReturnStableOrder() {
        String firstOrder = given().when().get(PROVIDERS).then().statusCode(200)
                .extract().jsonPath().getString("providers.provider");
        String secondOrder = given().when().get(PROVIDERS).then().statusCode(200)
                .extract().jsonPath().getString("providers.provider");

        org.assertj.core.api.Assertions.assertThat(secondOrder).isEqualTo(firstOrder);
    }

    // TC-506-B3: AVAIL-5.
    @Test
    @DisplayName("TC-506-B3: no Authorization header -> 200, never 401")
    void noAuthorizationHeaderStillReturns200() {
        given().when().get(PROVIDERS)
                .then().statusCode(200);
    }

    // TC-506-B4: AVAIL-BOOT-3 (regression, both /start endpoints still 200 when both configured).
    @Test
    @DisplayName("TC-506-B4: GET /oauth/{provider}/start still 200 for both providers when both configured")
    void bothStartEndpointsStillSucceedWhenBothConfigured() {
        given().when().get("/oauth/google/start").then().statusCode(200);
        given().when().get("/oauth/github/start").then().statusCode(200);
    }
}
