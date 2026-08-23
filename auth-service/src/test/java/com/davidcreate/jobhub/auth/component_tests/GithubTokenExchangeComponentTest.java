package com.davidcreate.jobhub.auth.component_tests;

import com.davidcreate.jobhub.auth.adapter.out.client.github.GithubOAuthProviderClient;
import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.component_tests.support.OAuthProviderStubs;
import com.davidcreate.jobhub.auth.component_tests.support.WireMockOAuthProvidersResource;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * The GitHub blank-access-token defect (ADR 0028, Decision 4; GH-EXCHANGE-*):
 * GitHub answers a misconfigured exchange with HTTP 200, an {@code error}
 * field, and no usable access token - never a 4xx. Default profile, shared
 * WireMock. Covers TC-506-B20..B22, B24..B26.
 */
@QuarkusTest
@QuarkusTestResource(WireMockOAuthProvidersResource.class)
@DisplayName("GitHub Token Exchange Component Tests (GH-EXCHANGE)")
class GithubTokenExchangeComponentTest {

    private static final String REGISTER = "/register";
    private static final String LOGIN = "/login";
    private static final String VERIFY_EMAIL_PATH = "/account/verify-email";
    private static final String PROVIDERS = "/oauth/providers";

    @InjectMock
    VerificationNotifier notifier;

    @BeforeEach
    void resetStubs() {
        OAuthProviderStubs.resetAll();
        Mockito.reset(notifier);
    }

    // TC-506-B20: GH-EXCHANGE-1 (the defect fix, load-bearing).
    @Test
    @DisplayName("TC-506-B20: GitHub 200+error+no access_token -> 401, provider-authorization-failed family, generic message")
    void blankAccessTokenNoKeyAtAllReturns401() {
        OAuthProviderStubs.stubGithubTokenBlankAccessToken("bad_verification_code",
                "The code passed is incorrect or expired.");

        githubCallback().then().statusCode(401)
                .body("error", equalTo("Provider Authorization Failed"))
                .body("message", equalTo("the identity provider rejected the authorization code"));
    }

    // TC-506-B21: GH-EXCHANGE-1 variant, access_token present but empty.
    @Test
    @DisplayName("TC-506-B21: GitHub 200 with access_token=\"\" -> identical 401 outcome to B20")
    void emptyStringAccessTokenReturns401() {
        OAuthProviderStubs.stubGithubTokenEmptyAccessToken("bad_verification_code");

        githubCallback().then().statusCode(401)
                .body("error", equalTo("Provider Authorization Failed"));
    }

    // TC-506-A35 / GH-EXCHANGE-2 (log-side counterpart of B22): the blank-token WARN
    // itself records GitHub's own error/error_description and never the token or
    // client secret. Reuses RequestLoggingFilterTest's CapturingHandler pattern
    // against GithubOAuthProviderClient's JBoss-logging-backed logger category.
    @Test
    @DisplayName("TC-506-A35: blank-access-token WARN records error/error_description, never the token or client secret")
    void blankAccessTokenWarnLogsErrorFieldsOnlyNeverTokenOrSecret() {
        Logger jbossBackedLogger = Logger.getLogger(GithubOAuthProviderClient.class.getName());
        CapturingHandler handler = new CapturingHandler();
        jbossBackedLogger.addHandler(handler);
        try {
            OAuthProviderStubs.stubGithubTokenBlankAccessToken("bad_verification_code",
                    "The code passed is incorrect or expired.");

            githubCallback().then().statusCode(401);

            assertThat(handler.warnings()).hasSize(1);
            String message = handler.warnings().get(0).getMessage();
            assertThat(message).contains("error=bad_verification_code");
            assertThat(message).contains("description=The code passed is incorrect or expired.");
            assertThat(message).doesNotContain("test-github-client-secret");
            assertThat(message).doesNotContain("access_token");
            assertThat(message).doesNotContain("Bearer");
        } finally {
            jbossBackedLogger.removeHandler(handler);
        }
    }

    // TC-506-B22: GH-EXCHANGE-2 (no leakage).
    @Test
    @DisplayName("TC-506-B22: the 401 response body never contains GitHub's raw error/error_description text")
    void responseNeverLeaksGithubInternals() {
        OAuthProviderStubs.stubGithubTokenBlankAccessToken("bad_verification_code",
                "The code passed is incorrect or expired, or the redirect_uri does not match.");

        String body = githubCallback().then().statusCode(401).extract().asString();

        assertThat(body).doesNotContain("bad_verification_code");
        assertThat(body).doesNotContain("redirect_uri does not match");
    }

    // TC-506-B24: GH-EXCHANGE-3 (no cascading failure).
    @Test
    @DisplayName("TC-506-B24: after a GitHub exchange failure, Google sign-in and password login both still work")
    void noCascadingFailureAfterGithubExchangeFailure() {
        OAuthProviderStubs.stubGithubTokenBlankAccessToken("bad_verification_code", "expired code");
        githubCallback().then().statusCode(401);

        String sub = "google-sub-" + UUID.randomUUID();
        String email = "unaffected-google-" + UUID.randomUUID() + "@example.com";
        OAuthProviderStubs.stubGoogleHappyPath(sub, email, true);
        googleCallback(sub).then().statusCode(200)
                .body("account.email", equalTo(email));

        String passwordEmail = "unaffected-password-" + UUID.randomUUID() + "@example.com";
        registerVerifyLogin(passwordEmail, "test1234");
    }

    // TC-506-B25: GH-EXCHANGE-4 (availability is configuration, not live health).
    @Test
    @DisplayName("TC-506-B25: GET /oauth/providers still reports github:true before AND after a failed exchange")
    void availabilityUnaffectedByLiveExchangeFailure() {
        given().when().get(PROVIDERS).then().statusCode(200)
                .body("providers.find { it.provider == 'github' }.available", equalTo(true));

        OAuthProviderStubs.stubGithubTokenBlankAccessToken("bad_verification_code", "expired code");
        githubCallback().then().statusCode(401);

        given().when().get(PROVIDERS).then().statusCode(200)
                .body("providers.find { it.provider == 'github' }.available", equalTo(true));
    }

    // TC-506-B26: GH-EXCHANGE-5, cites TC-459-B25 (regression, in OAuthCallbackComponentTest).
    @Test
    @DisplayName("TC-506-B26: Google's own 4xx token failure is unaffected by the github-only blank-token guard")
    void googleTokenFailureUnaffectedByGithubOnlyGuard() {
        OAuthProviderStubs.stubGoogleToken(400, "{\"error\":\"invalid_grant\"}");

        googleCallback("bad-code").then().statusCode(401);
    }

    // --- helpers ---

    private Response githubCallback() {
        String cookieState = startAndCaptureState("github");
        return given().contentType(ContentType.JSON)
                .cookie("oauth_state", cookieState)
                .body(Map.of("code", "auth-code-" + UUID.randomUUID(), "state", cookieState))
                .when().post("/oauth/github/callback");
    }

    private Response googleCallback(String sub) {
        String cookieState = startAndCaptureState("google");
        return given().contentType(ContentType.JSON)
                .cookie("oauth_state", cookieState)
                .body(Map.of("code", "auth-code-" + sub, "state", cookieState))
                .when().post("/oauth/google/callback");
    }

    private String startAndCaptureState(String provider) {
        Response response = given().when().get("/oauth/" + provider + "/start");
        response.then().statusCode(200);
        return response.getDetailedCookie("oauth_state").getValue();
    }

    private void registerVerifyLogin(String email, String password) {
        given().contentType(ContentType.JSON)
                .body(Map.of("firstName", "Jane", "lastName", "Doe", "email", email, "password", password))
                .when().post(REGISTER)
                .then().statusCode(201);

        ArgumentCaptor<String> codeCap = ArgumentCaptor.forClass(String.class);
        Mockito.verify(notifier).sendActionCode(anyString(), eq(VerificationAction.VERIFY_EMAIL), codeCap.capture());
        String code = codeCap.getValue();

        given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "code", code))
                .when().post(VERIFY_EMAIL_PATH)
                .then().statusCode(200);

        Mockito.reset(notifier);

        given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", password))
                .when().post(LOGIN)
                .then().statusCode(200);
    }

    private static class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        List<LogRecord> warnings() {
            // org.jboss.logging maps WARN to its own JDKLevel("WARN", 900) instance rather than
            // java.util.logging.Level.WARNING, so compare by severity, not by identity/name.
            return records.stream()
                    .filter(r -> r.getLevel().intValue() == Level.WARNING.intValue())
                    .toList();
        }
    }
}
