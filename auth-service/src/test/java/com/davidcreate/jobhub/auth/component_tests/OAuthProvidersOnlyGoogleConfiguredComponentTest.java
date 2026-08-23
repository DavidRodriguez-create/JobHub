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
 * AVAIL-BOOT-2: auth-service boots with exactly ONE provider configured
 * (google here; github's credentials are blanked). Google keeps the shared
 * test defaults (non-blank fake credentials from {@code application.properties}).
 * Isolated {@code component-tests-profiles} Surefire bucket. Covers
 * TC-506-B32, and folds in the AVAIL-7 bonus confirmation TC-506-B34 (a
 * genuinely PARTIAL github credential set - client-id present, client-secret
 * blank - is still unconfigured, identical to the fully-unset case).
 */
@QuarkusTest
@TestProfile(OAuthProvidersOnlyGoogleConfiguredComponentTest.OnlyGoogleConfiguredProfile.class)
@DisplayName("OAuth Providers - Only Google Configured (AVAIL-BOOT-2)")
class OAuthProvidersOnlyGoogleConfiguredComponentTest {

    public static class OnlyGoogleConfiguredProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // TC-506-B34 (bonus, AVAIL-7): github's client-id is a non-blank dummy
            // value while client-secret stays blank - a genuinely PARTIAL
            // credential set, not fully unset, still counts as unconfigured.
            return Map.of(
                    "auth.oauth.github.client-id", "partial-dummy-client-id",
                    "auth.oauth.github.client-secret", "");
        }
    }

    // TC-506-B32 (AVAIL-2): GET /oauth/providers -> 200, google available, github not.
    @Test
    @DisplayName("TC-506-B32: GET /oauth/providers -> 200, google:true, github:false")
    void onlyGoogleAvailable() {
        given().when().get("/oauth/providers")
                .then().statusCode(200)
                .body("providers[0].provider", equalTo("google"))
                .body("providers[0].available", equalTo(true))
                .body("providers[1].provider", equalTo("github"))
                .body("providers[1].available", equalTo(false));
    }

    @Test
    @DisplayName("TC-506-B32: GET /oauth/google/start -> 200 when google is configured")
    void googleStartSucceeds() {
        given().when().get("/oauth/google/start").then().statusCode(200);
    }

    // TC-506-B34 (bonus, AVAIL-7): partial github credentials -> 404 at /start too.
    @Test
    @DisplayName("TC-506-B32/B34: GET /oauth/github/start -> 404 with a partial (id-only) credential set")
    void githubStartReturns404WithPartialCredentials() {
        given().when().get("/oauth/github/start").then().statusCode(404);
    }
}
