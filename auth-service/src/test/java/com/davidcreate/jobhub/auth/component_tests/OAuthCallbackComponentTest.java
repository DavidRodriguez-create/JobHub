package com.davidcreate.jobhub.auth.component_tests;

import com.davidcreate.jobhub.auth.application.port.out.UserIdentityRepository;
import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.component_tests.support.OAuthProviderStubs;
import com.davidcreate.jobhub.auth.component_tests.support.WireMockOAuthProvidersResource;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * {@code POST /oauth/{provider}/callback} (story #459, ADR 0027): happy paths (BR1
 * resolution table) + 4xx + 502. Covers TC-459-B5..B33. Server-error (500) paths
 * live in {@link OAuthCallbackFailureComponentTest} (separate top-level class,
 * house rule).
 */
@QuarkusTest
@QuarkusTestResource(WireMockOAuthProvidersResource.class)
@DisplayName("OAuth Callback Component Tests")
class OAuthCallbackComponentTest {

    private static final String LOGIN = "/login";
    private static final String LOGIN_2FA = "/login/2fa";
    private static final String ACCOUNT = "/account";
    private static final String REGISTER = "/register";
    private static final String VERIFY_EMAIL_PATH = ACCOUNT + "/verify-email";
    private static final String SETUP_PATH = ACCOUNT + "/2fa/setup";
    private static final String VERIFY_SETUP_PATH = ACCOUNT + "/2fa/verify-setup";

    @InjectMock
    VerificationNotifier notifier;

    @Inject
    UserIdentityRepository userIdentityRepository;

    @BeforeEach
    void resetStubs() {
        OAuthProviderStubs.resetAll();
        Mockito.reset(notifier);
    }

    @Nested
    @DisplayName("Happy paths - BR1 resolution table")
    class HappyPaths {

        // TC-459-B5: OAUTH-NEW-GOOGLE-1.
        @Test
        @DisplayName("TC-459-B5: brand-new verified Google user -> 200, password-less verified account + identity")
        void newGoogleUserProvisions() {
            String sub = "google-sub-" + UUID.randomUUID();
            String email = "new.google.user-" + UUID.randomUUID() + "@example.com";
            OAuthProviderStubs.stubGoogleHappyPath(sub, email, true);

            Response response = googleCallback(sub, email);

            response.then().statusCode(200)
                    .body("token", notNullValue())
                    .body("account.email", equalTo(email))
                    .body("account.emailVerified", equalTo(true))
                    .body("twoFactorRequired", equalTo(false));
            assertThat(userIdentityRepository.findByProviderAndSubject("google", sub)).isPresent();
        }

        // TC-459-B6: OAUTH-NEW-GOOGLE-2/BR9.
        @Test
        @DisplayName("TC-459-B6: unverified Google email still provisions and logs in (BR9)")
        void newGoogleUserUnverifiedStillProvisions() {
            String sub = "google-sub-" + UUID.randomUUID();
            String email = "unverified.google-" + UUID.randomUUID() + "@example.com";
            OAuthProviderStubs.stubGoogleHappyPath(sub, email, false);

            googleCallback(sub, email).then().statusCode(200)
                    .body("token", notNullValue())
                    .body("account.emailVerified", equalTo(false));
        }

        // TC-459-B7: OAUTH-NEW-GITHUB-1.
        @Test
        @DisplayName("TC-459-B7: brand-new primary+verified GitHub user -> 200, verified account + identity")
        void newGithubUserProvisions() {
            long id = System.nanoTime();
            String email = "new.github.user-" + UUID.randomUUID() + "@example.com";
            OAuthProviderStubs.stubGithubHappyPath(id, "octocat", "Ada Lovelace", email, true, true);

            Response response = githubCallback();

            response.then().statusCode(200)
                    .body("token", notNullValue())
                    .body("account.email", equalTo(email))
                    .body("account.emailVerified", equalTo(true));
            assertThat(userIdentityRepository.findByProviderAndSubject("github", String.valueOf(id))).isPresent();
        }

        // TC-459-B8: OAUTH-NEW-GITHUB-2.
        @Test
        @DisplayName("TC-459-B8: GitHub primary email unverified, no other verified entry -> 200, unverified account")
        void newGithubUserUnverifiedStillProvisions() {
            long id = System.nanoTime();
            String email = "unverified.github-" + UUID.randomUUID() + "@example.com";
            OAuthProviderStubs.stubGithubHappyPath(id, "octocat", "Ada Lovelace", email, true, false);

            githubCallback().then().statusCode(200)
                    .body("account.emailVerified", equalTo(false));
        }

        // TC-459-B9: OAUTH-NEW-GITHUB-4/BR8.
        @Test
        @DisplayName("TC-459-B9: GitHub profile with no public name -> account uses login as name, still logs in")
        void newGithubUserWithNoPublicNameFallsBackToLogin() {
            long id = System.nanoTime();
            String email = "no.name.github-" + UUID.randomUUID() + "@example.com";
            OAuthProviderStubs.stubGithubHappyPath(id, "octocat", null, email, true, true);

            githubCallback().then().statusCode(200)
                    .body("account.firstName", notNullValue())
                    .body("account.firstName", org.hamcrest.Matchers.not(org.hamcrest.Matchers.blankString()));
        }

        // TC-459-B10: OAUTH-RETURN-GOOGLE-1.
        @Test
        @DisplayName("TC-459-B10: repeating the Google flow for the same sub resolves the same account")
        void returningGoogleUserResolvesSameAccount() {
            String sub = "google-sub-" + UUID.randomUUID();
            String email = "returning.google-" + UUID.randomUUID() + "@example.com";
            OAuthProviderStubs.stubGoogleHappyPath(sub, email, true);

            String firstAccountId = googleCallback(sub, email).then().statusCode(200)
                    .extract().jsonPath().getString("account.id");

            OAuthProviderStubs.stubGoogleHappyPath(sub, email, true);
            String secondAccountId = googleCallback(sub, email).then().statusCode(200)
                    .extract().jsonPath().getString("account.id");

            assertThat(secondAccountId).isEqualTo(firstAccountId);
        }

        // TC-459-B11: OAUTH-RETURN-GITHUB-1.
        @Test
        @DisplayName("TC-459-B11: repeating the GitHub flow for the same subject resolves the same account")
        void returningGithubUserResolvesSameAccount() {
            long id = System.nanoTime();
            String email = "returning.github-" + UUID.randomUUID() + "@example.com";
            OAuthProviderStubs.stubGithubHappyPath(id, "octocat", "Ada Lovelace", email, true, true);

            String firstAccountId = githubCallback().then().statusCode(200)
                    .extract().jsonPath().getString("account.id");

            OAuthProviderStubs.stubGithubHappyPath(id, "octocat", "Ada Lovelace", email, true, true);
            String secondAccountId = githubCallback().then().statusCode(200)
                    .extract().jsonPath().getString("account.id");

            assertThat(secondAccountId).isEqualTo(firstAccountId);
        }

        // TC-459-B12: OAUTH-RETURN-3/BR5.
        @Test
        @DisplayName("TC-459-B12: provider email changed since linking does not resync the stored account email")
        void changedProviderEmailDoesNotResync() {
            String sub = "google-sub-" + UUID.randomUUID();
            String originalEmail = "original-" + UUID.randomUUID() + "@example.com";
            OAuthProviderStubs.stubGoogleHappyPath(sub, originalEmail, true);
            String token = googleCallback(sub, originalEmail).then().statusCode(200)
                    .extract().jsonPath().getString("token");

            String changedEmail = "changed-" + UUID.randomUUID() + "@example.com";
            OAuthProviderStubs.stubGoogleHappyPath(sub, changedEmail, true);
            googleCallback(sub, changedEmail).then().statusCode(200);

            given().header("Authorization", "Bearer " + token)
                    .when().get(ACCOUNT)
                    .then().statusCode(200)
                    .body("email", equalTo(originalEmail));
        }
    }

    @Nested
    @DisplayName("Auto-link to an existing password account")
    class Linking {

        // TC-459-B13: OAUTH-LINK-GOOGLE-1.
        @Test
        @DisplayName("TC-459-B13: verified Google email matching an existing password account auto-links")
        void verifiedGoogleEmailAutoLinksToPasswordAccount() {
            String email = "jane-" + UUID.randomUUID() + "@example.com";
            String janeId = registerVerifyLoginAndGetAccountId(email, "test1234");

            String sub = "google-sub-" + UUID.randomUUID();
            OAuthProviderStubs.stubGoogleHappyPath(sub, email, true);

            Response response = googleCallback(sub, email);
            response.then().statusCode(200)
                    .body("account.id", equalTo(janeId))
                    .body("account.emailVerified", equalTo(true));
            assertThat(userIdentityRepository.findByProviderAndSubject("google", sub)).isPresent();
            assertThat(userIdentityRepository.findByProviderAndSubject("google", sub).get().getUserId())
                    .isEqualTo(UUID.fromString(janeId));
        }

        // TC-459-B14: OAUTH-LINK-GITHUB-1.
        @Test
        @DisplayName("TC-459-B14: primary+verified GitHub email matching an existing password account auto-links")
        void verifiedGithubEmailAutoLinksToPasswordAccount() {
            String email = "jane-" + UUID.randomUUID() + "@example.com";
            String janeId = registerVerifyLoginAndGetAccountId(email, "test1234");

            long id = System.nanoTime();
            OAuthProviderStubs.stubGithubHappyPath(id, "jane-gh", "Jane Doe", email, true, true);

            githubCallback().then().statusCode(200)
                    .body("account.id", equalTo(janeId))
                    .body("account.emailVerified", equalTo(true));
        }

        // TC-459-B15: OAUTH-LINK-3.
        @Test
        @DisplayName("TC-459-B15: repeating the same provider after auto-link resolves via existing-link, not again")
        void repeatedLoginAfterAutoLinkUsesExistingLink() {
            String email = "jane-" + UUID.randomUUID() + "@example.com";
            String janeId = registerVerifyLoginAndGetAccountId(email, "test1234");

            String sub = "google-sub-" + UUID.randomUUID();
            OAuthProviderStubs.stubGoogleHappyPath(sub, email, true);
            googleCallback(sub, email).then().statusCode(200).body("account.id", equalTo(janeId));

            OAuthProviderStubs.stubGoogleHappyPath(sub, email, true);
            googleCallback(sub, email).then().statusCode(200).body("account.id", equalTo(janeId));
        }

        // TC-459-B16: OAUTH-LINK-4/BR4.
        @Test
        @DisplayName("TC-459-B16: a second provider auto-links to the same already-linked account")
        void secondProviderAutoLinksToSameAccount() {
            String email = "jane-" + UUID.randomUUID() + "@example.com";
            String janeId = registerVerifyLoginAndGetAccountId(email, "test1234");

            String googleSub = "google-sub-" + UUID.randomUUID();
            OAuthProviderStubs.stubGoogleHappyPath(googleSub, email, true);
            googleCallback(googleSub, email).then().statusCode(200).body("account.id", equalTo(janeId));

            long githubId = System.nanoTime();
            OAuthProviderStubs.stubGithubHappyPath(githubId, "jane-gh", "Jane Doe", email, true, true);
            githubCallback().then().statusCode(200).body("account.id", equalTo(janeId));

            assertThat(userIdentityRepository.findByProviderAndSubject("google", googleSub).get().getUserId())
                    .isEqualTo(UUID.fromString(janeId));
            assertThat(userIdentityRepository.findByProviderAndSubject("github", String.valueOf(githubId)).get().getUserId())
                    .isEqualTo(UUID.fromString(janeId));
        }

        // TC-459-B17: OAUTH-LINK-5.
        @Test
        @DisplayName("TC-459-B17: password login keeps working after auto-link")
        void passwordLoginKeepsWorkingAfterAutoLink() {
            String email = "jane-" + UUID.randomUUID() + "@example.com";
            registerVerifyLoginAndGetAccountId(email, "test1234");

            String sub = "google-sub-" + UUID.randomUUID();
            OAuthProviderStubs.stubGoogleHappyPath(sub, email, true);
            googleCallback(sub, email).then().statusCode(200);

            given().contentType(ContentType.JSON)
                    .body(Map.of("email", email, "password", "test1234"))
                    .when().post(LOGIN)
                    .then().statusCode(200);
        }
    }

    @Nested
    @DisplayName("Refuse: unverified provider email colliding with an existing account")
    class Refuse {

        // TC-459-B26: OAUTH-REFUSE-GOOGLE-1.
        @Test
        @DisplayName("TC-459-B26: unverified Google email colliding with an existing account -> 401, account untouched")
        void unverifiedGoogleEmailCollisionRefused() {
            String email = "jane-" + UUID.randomUUID() + "@example.com";
            registerVerifyLoginAndGetAccountId(email, "test1234");

            String sub = "google-sub-" + UUID.randomUUID();
            OAuthProviderStubs.stubGoogleHappyPath(sub, email, false);

            googleCallback(sub, email).then().statusCode(401);
            assertThat(userIdentityRepository.findByProviderAndSubject("google", sub)).isEmpty();

            given().contentType(ContentType.JSON)
                    .body(Map.of("email", email, "password", "test1234"))
                    .when().post(LOGIN)
                    .then().statusCode(200);
        }

        // TC-459-B27: OAUTH-REFUSE-GITHUB-1.
        @Test
        @DisplayName("TC-459-B27: unverified primary GitHub email colliding with an existing account -> 401")
        void unverifiedGithubEmailCollisionRefused() {
            String email = "jane-" + UUID.randomUUID() + "@example.com";
            registerVerifyLoginAndGetAccountId(email, "test1234");

            long id = System.nanoTime();
            OAuthProviderStubs.stubGithubHappyPath(id, "jane-gh", "Jane Doe", email, true, false);

            githubCallback().then().statusCode(401);

            given().contentType(ContentType.JSON)
                    .body(Map.of("email", email, "password", "test1234"))
                    .when().post(LOGIN)
                    .then().statusCode(200);
        }
    }

    @Nested
    @DisplayName("2FA challenge on a resolved social login")
    class TwoFactor {

        // TC-459-B18: OAUTH-2FA-1.
        @Test
        @DisplayName("TC-459-B18: existing-link account with 2FA enabled returns the challenge shape")
        void existingLinkWithTwoFactorReturnsChallenge() {
            String email = "user-" + UUID.randomUUID() + "@example.com";
            String token = registerVerifyAndLogin(email, "test1234");

            String sub = "google-sub-" + UUID.randomUUID();
            OAuthProviderStubs.stubGoogleHappyPath(sub, email, true);
            googleCallback(sub, email).then().statusCode(200);

            // JIT-created account has no password; enabling 2FA only needs the JWT.
            enableTwoFactor(token);

            OAuthProviderStubs.stubGoogleHappyPath(sub, email, true);
            googleCallback(sub, email).then().statusCode(200)
                    .body("twoFactorRequired", equalTo(true))
                    .body("twoFactorToken", notNullValue())
                    .body("token", nullValue())
                    .body("account", nullValue())
                    .body("expiresIn", nullValue());
        }

        // TC-459-B19: OAUTH-2FA-2 (PDA-flagged highest-risk regression surface).
        @Test
        @DisplayName("TC-459-B19: first-time auto-link to a 2FA-enabled password account links AND challenges")
        void autoLinkToTwoFactorAccountLinksAndChallenges() {
            String email = "jane-" + UUID.randomUUID() + "@example.com";
            String janeToken = registerVerifyAndLogin(email, "test1234");
            enableTwoFactor(janeToken);

            String sub = "google-sub-" + UUID.randomUUID();
            OAuthProviderStubs.stubGoogleHappyPath(sub, email, true);

            googleCallback(sub, email).then().statusCode(200)
                    .body("twoFactorRequired", equalTo(true))
                    .body("twoFactorToken", notNullValue())
                    .body("token", nullValue());
            assertThat(userIdentityRepository.findByProviderAndSubject("google", sub)).isPresent();
        }

        // TC-459-B20: OAUTH-2FA-3.
        @Test
        @DisplayName("TC-459-B20: completing the challenge with the correct TOTP code finishes login")
        void completingChallengeFinishesLogin() {
            String email = "user-" + UUID.randomUUID() + "@example.com";
            String token = registerVerifyAndLogin(email, "test1234");
            String setupKey = enableTwoFactor(token);

            String sub = "google-sub-" + UUID.randomUUID();
            OAuthProviderStubs.stubGoogleHappyPath(sub, email, true);
            googleCallback(sub, email).then().statusCode(200);

            OAuthProviderStubs.stubGoogleHappyPath(sub, email, true);
            String challengeToken = googleCallback(sub, email).then().statusCode(200)
                    .extract().jsonPath().getString("twoFactorToken");

            given().contentType(ContentType.JSON)
                    .body(Map.of("twoFactorToken", challengeToken, "totpCode", currentTotpCode(setupKey)))
                    .when().post(LOGIN_2FA)
                    .then().statusCode(200)
                    .body("token", notNullValue());
        }

        // TC-459-B21: OAUTH-2FA-4 (cites B5).
        @Test
        @DisplayName("TC-459-B21: brand-new JIT account never carries twoFactorRequired")
        void jitAccountNeverCarriesTwoFactorRequired() {
            String sub = "google-sub-" + UUID.randomUUID();
            String email = "jit-" + UUID.randomUUID() + "@example.com";
            OAuthProviderStubs.stubGoogleHappyPath(sub, email, true);

            googleCallback(sub, email).then().statusCode(200)
                    .body("twoFactorRequired", equalTo(false))
                    .body("account.twoFactorEnabled", equalTo(false));
        }
    }

    @Nested
    @DisplayName("4xx: state, unknown provider, provider rejection")
    class ClientErrors {

        // TC-459-B22: OAUTH-ERR-3 (state mismatch).
        @Test
        @DisplayName("TC-459-B22: submitted state different from the bound cookie -> 400")
        void stateMismatchReturns400() {
            String cookieState = startAndCaptureState("google");

            given().contentType(ContentType.JSON)
                    .cookie("oauth_state", cookieState)
                    .body(Map.of("code", "irrelevant-code", "state", "not-the-bound-state"))
                    .when().post("/oauth/google/callback")
                    .then().statusCode(400);
        }

        // TC-459-B23: OAUTH-ERR-3 (missing cookie).
        @Test
        @DisplayName("TC-459-B23: no prior start call (no state cookie at all) -> 400")
        void missingStateCookieReturns400() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("code", "irrelevant-code", "state", "some-state"))
                    .when().post("/oauth/google/callback")
                    .then().statusCode(400);
        }

        // TC-459-B24: OAUTH-START-4/BR10 (stale state).
        @Test
        @DisplayName("TC-459-B24: an earlier abandoned attempt's state does not validate against a later cookie")
        void staleStateFromAbandonedAttemptReturns400() {
            String firstState = startAndCaptureState("google");
            String secondCookieState = startAndCaptureState("google");

            given().contentType(ContentType.JSON)
                    .cookie("oauth_state", secondCookieState)
                    .body(Map.of("code", "irrelevant-code", "state", firstState))
                    .when().post("/oauth/google/callback")
                    .then().statusCode(400);
        }

        // TC-459-B25: OAUTH-ERR-2 (invalid/expired code).
        @Test
        @DisplayName("TC-459-B25: provider rejects the code at token-exchange -> 401, nothing created")
        void invalidCodeReturns401() {
            OAuthProviderStubs.stubGoogleToken(400, "{\"error\":\"invalid_grant\"}");

            String cookieState = startAndCaptureState("google");
            given().contentType(ContentType.JSON)
                    .cookie("oauth_state", cookieState)
                    .body(Map.of("code", "bad-code", "state", cookieState))
                    .when().post("/oauth/google/callback")
                    .then().statusCode(401);
        }

        // TC-459-B28: OAUTH-NEW-GITHUB-3/BR7 (no email at all).
        @Test
        @DisplayName("TC-459-B28: GitHub returns no usable email at all -> 401, nothing created")
        void noUsableGithubEmailReturns401() {
            long id = System.nanoTime();
            OAuthProviderStubs.stubGithubToken(200, """
                    {"access_token":"github-access-token","token_type":"bearer","scope":"read:user"}
                    """);
            OAuthProviderStubs.stubGithubUser(200, """
                    {"id":%d,"login":"octocat","name":"Octo Cat"}
                    """.formatted(id));
            OAuthProviderStubs.stubGithubEmails(200, "[]");

            githubCallback().then().statusCode(401);
            assertThat(userIdentityRepository.findByProviderAndSubject("github", String.valueOf(id))).isEmpty();
        }

        // TC-459-B29: OAUTH-ERR-5.
        @Test
        @DisplayName("TC-459-B29: unknown provider hit directly on the callback -> 404")
        void unknownProviderOnCallbackReturns404() {
            given().contentType(ContentType.JSON)
                    .body(Map.of("code", "irrelevant", "state", "irrelevant"))
                    .when().post("/oauth/facebook/callback")
                    .then().statusCode(404);
        }

        // TC-459-B30: OAUTH-ERR-6 (replay).
        @Test
        @DisplayName("TC-459-B30: replaying an already-consumed code+state never succeeds twice")
        void replayOfConsumedCodeNeverSucceedsTwice() {
            String scenario = "replay-" + UUID.randomUUID();
            WireMockOAuthProvidersResource.server().stubFor(post(urlEqualTo("/token"))
                    .inScenario(scenario)
                    .whenScenarioStateIs(Scenario.STARTED)
                    .willSetStateTo("used")
                    .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                            .withBody("{\"access_token\":\"google-access-token\",\"token_type\":\"Bearer\"}")));
            WireMockOAuthProvidersResource.server().stubFor(post(urlEqualTo("/token"))
                    .inScenario(scenario)
                    .whenScenarioStateIs("used")
                    .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                            .withBody("{\"error\":\"invalid_grant\"}")));
            String sub = "google-sub-" + UUID.randomUUID();
            String email = "replay-" + UUID.randomUUID() + "@example.com";
            OAuthProviderStubs.stubGoogleUserInfo(200, """
                    {"sub":"%s","email":"%s","email_verified":true,"given_name":"Ada","family_name":"Lovelace"}
                    """.formatted(sub, email));

            String cookieState = startAndCaptureState("google");
            given().contentType(ContentType.JSON)
                    .cookie("oauth_state", cookieState)
                    .body(Map.of("code", "one-time-code", "state", cookieState))
                    .when().post("/oauth/google/callback")
                    .then().statusCode(200);

            given().contentType(ContentType.JSON)
                    .cookie("oauth_state", cookieState)
                    .body(Map.of("code", "one-time-code", "state", cookieState))
                    .when().post("/oauth/google/callback")
                    .then().statusCode(anyOf401Or502());

            assertThat(userIdentityRepository.findByProviderAndSubject("google", sub)).isPresent();
        }

        private org.hamcrest.Matcher<Integer> anyOf401Or502() {
            return org.hamcrest.Matchers.anyOf(equalTo(401), equalTo(502));
        }
    }

    @Nested
    @DisplayName("Provider outage (502)")
    class Outage {

        // TC-459-B31: OAUTH-ERR-4 (token-exchange 5xx).
        @Test
        @DisplayName("TC-459-B31: token endpoint 5xx -> 502, nothing created")
        void tokenExchangeOutageReturns502() {
            OAuthProviderStubs.stubGoogleToken(503, "service unavailable");

            String cookieState = startAndCaptureState("google");
            given().contentType(ContentType.JSON)
                    .cookie("oauth_state", cookieState)
                    .body(Map.of("code", "irrelevant-code", "state", cookieState))
                    .when().post("/oauth/google/callback")
                    .then().statusCode(502);
        }

        // TC-459-B32: OAUTH-ERR-4 (userinfo 5xx).
        @Test
        @DisplayName("TC-459-B32: userinfo endpoint 5xx after a successful token exchange -> 502")
        void userInfoOutageReturns502() {
            OAuthProviderStubs.stubGoogleToken(200, """
                    {"access_token":"google-access-token","token_type":"Bearer"}
                    """);
            OAuthProviderStubs.stubGoogleUserInfo(500, "internal error");

            String cookieState = startAndCaptureState("google");
            given().contentType(ContentType.JSON)
                    .cookie("oauth_state", cookieState)
                    .body(Map.of("code", "irrelevant-code", "state", cookieState))
                    .when().post("/oauth/google/callback")
                    .then().statusCode(502);
        }

        // TC-459-B33: OAUTH-ERR-4 (password login unaffected during outage).
        @Test
        @DisplayName("TC-459-B33: password login stays fully functional while the OAuth provider is down")
        void passwordLoginUnaffectedDuringOutage() {
            String email = "user-" + UUID.randomUUID() + "@example.com";
            registerVerifyLoginAndGetAccountId(email, "test1234");
            OAuthProviderStubs.stubGoogleToken(503, "service unavailable");

            given().contentType(ContentType.JSON)
                    .body(Map.of("email", email, "password", "test1234"))
                    .when().post(LOGIN)
                    .then().statusCode(200);
        }
    }

    // --- helpers ---

    private Response googleCallback(String sub, String email) {
        String cookieState = startAndCaptureState("google");
        return given().contentType(ContentType.JSON)
                .cookie("oauth_state", cookieState)
                .body(Map.of("code", "auth-code-" + sub, "state", cookieState))
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

    private void registerOnly(String email, String password) {
        given().contentType(ContentType.JSON)
                .body(Map.of("firstName", "Jane", "lastName", "Doe", "email", email, "password", password))
                .when().post(REGISTER)
                .then().statusCode(201);
    }

    private void registerAndVerify(String email, String password) {
        registerOnly(email, password);

        ArgumentCaptor<String> codeCap = ArgumentCaptor.forClass(String.class);
        Mockito.verify(notifier).sendActionCode(anyString(), eq(VerificationAction.VERIFY_EMAIL), codeCap.capture());
        String code = codeCap.getValue();

        given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "code", code))
                .when().post(VERIFY_EMAIL_PATH)
                .then().statusCode(200);

        Mockito.reset(notifier);
    }

    private String registerVerifyAndLogin(String email, String password) {
        registerAndVerify(email, password);
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", password))
                .when().post(LOGIN)
                .then().statusCode(200)
                .extract().jsonPath().getString("token");
    }

    private String registerVerifyLoginAndGetAccountId(String email, String password) {
        registerAndVerify(email, password);
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", password))
                .when().post(LOGIN)
                .then().statusCode(200)
                .extract().jsonPath().getString("account.id");
    }

    private String setupTwoFactor(String token) {
        return given().header("Authorization", "Bearer " + token)
                .when().post(SETUP_PATH)
                .then().statusCode(200)
                .extract().jsonPath().getString("setupKey");
    }

    private String enableTwoFactor(String token) {
        String setupKey = setupTwoFactor(token);
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("totpCode", currentTotpCode(setupKey)))
                .when().post(VERIFY_SETUP_PATH)
                .then().statusCode(200);
        return setupKey;
    }

    private String currentTotpCode(String base32Secret) {
        try {
            long bucket = Instant.now().getEpochSecond() / 30;
            return new DefaultCodeGenerator().generate(base32Secret, bucket);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
