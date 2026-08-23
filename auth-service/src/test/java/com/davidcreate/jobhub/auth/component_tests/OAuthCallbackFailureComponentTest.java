package com.davidcreate.jobhub.auth.component_tests;

import com.davidcreate.jobhub.auth.application.port.out.UserIdentityRepository;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.component_tests.support.OAuthProviderStubs;
import com.davidcreate.jobhub.auth.component_tests.support.WireMockOAuthProvidersResource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@code POST /oauth/google/callback} server-error (500) paths (story #459, ADR
 * 0027). WireMock still returns a fully valid provider response in every case here
 * (proves the 500 comes from auth-service's own DB write, not an unstubbed provider
 * call - mirrors story #384's ordering discipline for job-service). Separate
 * top-level {@code @QuarkusTest} class per house rule: mixing {@code @InjectMock}
 * repositories with real DevServices beans in the same class as
 * {@link OAuthCallbackComponentTest} would lose the real DB for the happy paths.
 *
 * <p>ADR 0027 keeps {@code state} cookie-bound only (no DB table), so
 * {@code GET /oauth/google/start} has no injectable failure point - TC-459-C3 does
 * not apply here (noted, not forced).
 */
@QuarkusTest
@QuarkusTestResource(WireMockOAuthProvidersResource.class)
@DisplayName("OAuth Callback Failure Component Tests")
class OAuthCallbackFailureComponentTest {

    @InjectMock
    UserRepository userRepository;

    @InjectMock
    UserIdentityRepository userIdentityRepository;

    @BeforeEach
    void resetStubs() {
        OAuthProviderStubs.resetAll();
    }

    // TC-459-C1
    @Test
    @DisplayName("TC-459-C1: UserRepository crash on a valid callback -> 500")
    void userRepositoryCrashReturns500() {
        String sub = "google-sub-" + UUID.randomUUID();
        String email = "crash-user-" + UUID.randomUUID() + "@example.com";
        OAuthProviderStubs.stubGoogleHappyPath(sub, email, true);
        when(userIdentityRepository.findByProviderAndSubject(anyString(), anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(anyString())).thenThrow(new RuntimeException("simulated DB crash"));

        callback(sub).then().statusCode(500);
    }

    // TC-459-C2
    @Test
    @DisplayName("TC-459-C2: UserIdentityRepository crash on a valid callback -> 500")
    void userIdentityRepositoryCrashReturns500() {
        String sub = "google-sub-" + UUID.randomUUID();
        String email = "crash-identity-" + UUID.randomUUID() + "@example.com";
        OAuthProviderStubs.stubGoogleHappyPath(sub, email, true);
        when(userIdentityRepository.findByProviderAndSubject(anyString(), anyString()))
                .thenThrow(new RuntimeException("simulated DB crash"));

        callback(sub).then().statusCode(500);
    }

    private Response callback(String sub) {
        String cookieState = startAndCaptureState();
        return given().contentType(ContentType.JSON)
                .cookie("oauth_state", cookieState)
                .body(Map.of("code", "auth-code-" + sub, "state", cookieState))
                .when().post("/oauth/google/callback");
    }

    private String startAndCaptureState() {
        Response response = given().when().get("/oauth/google/start");
        response.then().statusCode(200);
        return response.getDetailedCookie("oauth_state").getValue();
    }
}
