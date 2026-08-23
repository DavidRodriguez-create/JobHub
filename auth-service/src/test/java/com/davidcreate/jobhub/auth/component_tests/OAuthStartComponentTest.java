package com.davidcreate.jobhub.auth.component_tests;

import com.davidcreate.jobhub.auth.component_tests.support.WireMockOAuthProvidersResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /oauth/{provider}/start} (story #459, ADR 0027). Covers TC-459-B1..B4.
 */
@QuarkusTest
@QuarkusTestResource(WireMockOAuthProvidersResource.class)
@DisplayName("OAuth Start Component Tests")
class OAuthStartComponentTest {

    // TC-459-B1: OAUTH-START-1.
    @Test
    @DisplayName("TC-459-B1: GET /oauth/google/start -> 200, authorizationUrl targets Google, HttpOnly state cookie")
    void googleStartReturnsAuthorizationUrlAndBindsState() {
        Response response = given().when().get("/oauth/google/start");

        response.then().statusCode(200)
                .body("authorizationUrl", org.hamcrest.Matchers.containsString("accounts.google.com"))
                .body("authorizationUrl", org.hamcrest.Matchers.containsString("client_id="))
                .body("authorizationUrl", org.hamcrest.Matchers.containsString("state="));

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).containsIgnoringCase("HttpOnly");
    }

    // TC-459-B2: OAUTH-START-2.
    @Test
    @DisplayName("TC-459-B2: GET /oauth/github/start -> 200, authorizationUrl targets GitHub, scope includes user:email")
    void githubStartReturnsAuthorizationUrlWithEmailScope() {
        Response response = given().when().get("/oauth/github/start");

        response.then().statusCode(200)
                .body("authorizationUrl", org.hamcrest.Matchers.containsString("github.com"))
                .body("authorizationUrl", org.hamcrest.Matchers.containsString("user%3Aemail"));

        assertThat(response.getHeader("Set-Cookie")).containsIgnoringCase("HttpOnly");
    }

    // TC-459-B3: OAUTH-START-3.
    @Test
    @DisplayName("TC-459-B3: GET /oauth/facebook/start -> 404, no state cookie bound")
    void unknownProviderReturns404WithoutBindingState() {
        Response response = given().when().get("/oauth/facebook/start");

        response.then().statusCode(404);
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    // TC-459-B4: OAUTH-START-4/BR10.
    @Test
    @DisplayName("TC-459-B4: two start() calls in a row bind different state cookie values")
    void twoStartCallsBindDifferentState() {
        String firstState = extractStateCookieValue(given().when().get("/oauth/google/start"));
        String secondState = extractStateCookieValue(given().when().get("/oauth/google/start"));

        assertThat(firstState).isNotBlank();
        assertThat(secondState).isNotBlank();
        assertThat(firstState).isNotEqualTo(secondState);
    }

    private String extractStateCookieValue(Response response) {
        return response.getDetailedCookie("oauth_state").getValue();
    }
}
