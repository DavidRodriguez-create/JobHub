package com.davidcreate.jobhub.auth.component_tests;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.UserEntity;
import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.UserIdentityEntity;
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

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * No partial account, no partial session on a failed callback (ADR 0028,
 * Decision 3; RB-BR1/ROLLBACK-*; the "inside the profile" report). Default
 * profile, shared WireMock. Covers TC-506-B18..B19.
 */
@QuarkusTest
@QuarkusTestResource(WireMockOAuthProvidersResource.class)
@DisplayName("OAuth Callback Rollback Component Tests")
class OAuthCallbackRollbackComponentTest {

    private static final String REGISTER = "/register";
    private static final String LOGIN = "/login";
    private static final String VERIFY_EMAIL_PATH = "/account/verify-email";

    @InjectMock
    VerificationNotifier notifier;

    @BeforeEach
    void resetStubs() {
        OAuthProviderStubs.resetAll();
        Mockito.reset(notifier);
    }

    // TC-506-B18 (400 family): state mismatch.
    @Test
    @DisplayName("TC-506-B18: state mismatch (400) leaves auth.user/user_identity row counts unchanged")
    void stateMismatchLeavesRowCountsUnchanged() {
        long usersBefore = UserEntity.count();
        long identitiesBefore = UserIdentityEntity.count();

        String cookieState = startAndCaptureState("google");
        given().contentType(ContentType.JSON)
                .cookie("oauth_state", cookieState)
                .body(Map.of("code", "irrelevant", "state", "not-the-bound-state"))
                .when().post("/oauth/google/callback")
                .then().statusCode(400);

        assertThat(UserEntity.count()).isEqualTo(usersBefore);
        assertThat(UserIdentityEntity.count()).isEqualTo(identitiesBefore);
    }

    // TC-506-B18 (401 family): provider rejects the code at token exchange.
    @Test
    @DisplayName("TC-506-B18: provider-auth-failure (401) leaves row counts unchanged")
    void providerAuthFailureLeavesRowCountsUnchanged() {
        OAuthProviderStubs.stubGoogleToken(400, "{\"error\":\"invalid_grant\"}");
        long usersBefore = UserEntity.count();
        long identitiesBefore = UserIdentityEntity.count();

        googleCallback("bad-code").then().statusCode(401);

        assertThat(UserEntity.count()).isEqualTo(usersBefore);
        assertThat(UserIdentityEntity.count()).isEqualTo(identitiesBefore);
    }

    // TC-506-B18 (404 family): unknown/unconfigured provider.
    @Test
    @DisplayName("TC-506-B18: unknown provider (404) leaves row counts unchanged")
    void unknownProviderLeavesRowCountsUnchanged() {
        long usersBefore = UserEntity.count();
        long identitiesBefore = UserIdentityEntity.count();

        given().contentType(ContentType.JSON)
                .body(Map.of("code", "irrelevant", "state", "irrelevant"))
                .when().post("/oauth/facebook/callback")
                .then().statusCode(404);

        assertThat(UserEntity.count()).isEqualTo(usersBefore);
        assertThat(UserIdentityEntity.count()).isEqualTo(identitiesBefore);
    }

    // TC-506-B18 (401 family, GH-EXCHANGE-1): GitHub blank-access-token.
    @Test
    @DisplayName("TC-506-B18: GitHub blank-access-token (401) leaves row counts unchanged")
    void githubBlankAccessTokenLeavesRowCountsUnchanged() {
        OAuthProviderStubs.stubGithubTokenBlankAccessToken("bad_verification_code", "The code passed is incorrect or expired.");
        long usersBefore = UserEntity.count();
        long identitiesBefore = UserIdentityEntity.count();

        githubCallback().then().statusCode(401);

        assertThat(UserEntity.count()).isEqualTo(usersBefore);
        assertThat(UserIdentityEntity.count()).isEqualTo(identitiesBefore);
    }

    // TC-506-B18 (401 family): unverified-email collision refused.
    @Test
    @DisplayName("TC-506-B18: unverified-email collision refusal (401) leaves row counts unchanged")
    void unverifiedEmailCollisionLeavesRowCountsUnchanged() {
        String email = "jane-" + UUID.randomUUID() + "@example.com";
        registerVerifyLogin(email, "test1234");
        String sub = "google-sub-" + UUID.randomUUID();
        OAuthProviderStubs.stubGoogleHappyPath(sub, email, false);

        long usersBefore = UserEntity.count();
        long identitiesBefore = UserIdentityEntity.count();

        googleCallback("auth-code-" + sub).then().statusCode(401);

        assertThat(UserEntity.count()).isEqualTo(usersBefore);
        assertThat(UserIdentityEntity.count()).isEqualTo(identitiesBefore);
    }

    // TC-506-B18 (401 family): no usable email at all.
    @Test
    @DisplayName("TC-506-B18: no usable email at all (401) leaves row counts unchanged")
    void noUsableEmailLeavesRowCountsUnchanged() {
        long id = System.nanoTime();
        OAuthProviderStubs.stubGithubToken(200, """
                {"access_token":"github-access-token","token_type":"bearer","scope":"read:user"}
                """);
        OAuthProviderStubs.stubGithubUser(200, """
                {"id":%d,"login":"octocat","name":"Octo Cat"}
                """.formatted(id));
        OAuthProviderStubs.stubGithubEmails(200, "[]");

        long usersBefore = UserEntity.count();
        long identitiesBefore = UserIdentityEntity.count();

        githubCallback().then().statusCode(401);

        assertThat(UserEntity.count()).isEqualTo(usersBefore);
        assertThat(UserIdentityEntity.count()).isEqualTo(identitiesBefore);
    }

    // TC-506-B18 (502 family): provider outage.
    @Test
    @DisplayName("TC-506-B18: provider outage (502) leaves row counts unchanged")
    void providerOutageLeavesRowCountsUnchanged() {
        OAuthProviderStubs.stubGoogleToken(503, "service unavailable");
        long usersBefore = UserEntity.count();
        long identitiesBefore = UserIdentityEntity.count();

        googleCallback("irrelevant-code").then().statusCode(502);

        assertThat(UserEntity.count()).isEqualTo(usersBefore);
        assertThat(UserIdentityEntity.count()).isEqualTo(identitiesBefore);
    }

    // TC-506-B19: ROLLBACK-4. A fresh attempt right after a failure succeeds normally.
    @Test
    @DisplayName("TC-506-B19: a fresh attempt right after a failed callback succeeds exactly as if nothing happened")
    void freshAttemptAfterFailureSucceedsNormally() {
        OAuthProviderStubs.stubGoogleToken(400, "{\"error\":\"invalid_grant\"}");
        googleCallback("bad-code").then().statusCode(401);

        String sub = "google-sub-" + UUID.randomUUID();
        String email = "retry-" + UUID.randomUUID() + "@example.com";
        OAuthProviderStubs.stubGoogleHappyPath(sub, email, true);

        googleCallback("auth-code-" + sub).then().statusCode(200)
                .body("account.email", equalTo(email));

        // A normal password login for a seeded account also still works.
        String seededEmail = "seeded-" + UUID.randomUUID() + "@example.com";
        registerVerifyLogin(seededEmail, "test1234");
        given().contentType(ContentType.JSON)
                .body(Map.of("email", seededEmail, "password", "test1234"))
                .when().post(LOGIN)
                .then().statusCode(200);
    }

    // --- helpers ---

    private Response googleCallback(String code) {
        String cookieState = startAndCaptureState("google");
        return given().contentType(ContentType.JSON)
                .cookie("oauth_state", cookieState)
                .body(Map.of("code", code, "state", cookieState))
                .when().post("/oauth/google/callback");
    }

    private Response githubCallback() {
        String cookieState = startAndCaptureState("github");
        return given().contentType(ContentType.JSON)
                .cookie("oauth_state", cookieState)
                .body(Map.of("code", "auth-code-" + UUID.randomUUID(), "state", cookieState))
                .when().post("/oauth/github/callback");
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
}
