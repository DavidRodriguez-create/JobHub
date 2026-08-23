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
 * AVAIL-BOOT-1: auth-service boots with ZERO providers configured (the shipped
 * {@code .env.example} default) instead of crash-looping (ADR 0028, Decision
 * 1). The class existing and running at all IS the proof: pre-fix, a plain
 * {@code String} {@code @ConfigProperty} injection point with an empty
 * {@code defaultValue} would fail Quarkus's config validation at startup for
 * this exact profile. Isolated {@code component-tests-profiles} Surefire
 * bucket (own fresh fork) per the QA doc's recorded approach - see
 * {@code auth-service/pom.xml}. Covers TC-506-B31.
 */
@QuarkusTest
@TestProfile(OAuthProvidersNoneConfiguredComponentTest.NoneConfiguredProfile.class)
@DisplayName("OAuth Providers - Neither Configured (AVAIL-BOOT-1)")
class OAuthProvidersNoneConfiguredComponentTest {

    public static class NoneConfiguredProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "auth.oauth.google.client-id", "",
                    "auth.oauth.google.client-secret", "",
                    "auth.oauth.github.client-id", "",
                    "auth.oauth.github.client-secret", "");
        }
    }

    // TC-506-B31 (AVAIL-1): GET /oauth/providers -> 200, both unavailable.
    @Test
    @DisplayName("TC-506-B31: GET /oauth/providers -> 200, google and github both available:false")
    void bothProvidersUnavailable() {
        given().when().get("/oauth/providers")
                .then().statusCode(200)
                .body("providers[0].provider", equalTo("google"))
                .body("providers[0].available", equalTo(false))
                .body("providers[1].provider", equalTo("github"))
                .body("providers[1].available", equalTo(false));
    }

    // TC-506-B31 (regression of OAUTH-START-3, reachable for the first time).
    @Test
    @DisplayName("TC-506-B31: GET /oauth/google/start -> 404 when unconfigured")
    void googleStartReturns404() {
        given().when().get("/oauth/google/start").then().statusCode(404);
    }

    @Test
    @DisplayName("TC-506-B31: GET /oauth/github/start -> 404 when unconfigured")
    void githubStartReturns404() {
        given().when().get("/oauth/github/start").then().statusCode(404);
    }
}
