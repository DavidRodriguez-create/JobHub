package com.davidcreate.jobhub.auth.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * AVAIL-BOOT-2 mirror: auth-service boots with exactly ONE provider configured
 * (github here; google's credentials are blanked). Isolated
 * {@code component-tests-profiles} Surefire bucket. Covers TC-506-B33.
 */
@QuarkusTest
@TestProfile(OAuthProvidersOnlyGithubConfiguredComponentTest.OnlyGithubConfiguredProfile.class)
@DisplayName("OAuth Providers - Only GitHub Configured (AVAIL-BOOT-2)")
class OAuthProvidersOnlyGithubConfiguredComponentTest {

    public static class OnlyGithubConfiguredProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "auth.oauth.google.client-id", "",
                    "auth.oauth.google.client-secret", "");
        }
    }

    // TC-506-B33 (AVAIL-3): GET /oauth/providers -> 200, github available, google not.
    @Test
    @DisplayName("TC-506-B33: GET /oauth/providers -> 200, google:false, github:true")
    void onlyGithubAvailable() {
        given().when().get("/oauth/providers")
                .then().statusCode(200)
                .body("providers[0].provider", equalTo("google"))
                .body("providers[0].available", equalTo(false))
                .body("providers[1].provider", equalTo("github"))
                .body("providers[1].available", equalTo(true));
    }

    @Test
    @DisplayName("TC-506-B33: GET /oauth/google/start -> 404 when google is unconfigured")
    void googleStartReturns404() {
        given().when().get("/oauth/google/start").then().statusCode(404);
    }

    @Test
    @DisplayName("TC-506-B33: GET /oauth/github/start -> 200 when github is configured")
    void githubStartSucceeds() {
        given().when().get("/oauth/github/start").then().statusCode(200);
    }
}
