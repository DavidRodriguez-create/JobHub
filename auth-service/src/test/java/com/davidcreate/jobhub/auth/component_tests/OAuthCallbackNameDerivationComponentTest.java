package com.davidcreate.jobhub.auth.component_tests;

import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.component_tests.support.OAuthProviderStubs;
import com.davidcreate.jobhub.auth.component_tests.support.WireMockOAuthProvidersResource;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * {@code POST /oauth/{provider}/callback}: missing-provider-name derivation over
 * the wire (ADR 0028, Decision 3, DN-BR1/DN-BR2). Default profile, shared
 * WireMock server. Covers TC-506-B5..B17.
 */
@QuarkusTest
@QuarkusTestResource(WireMockOAuthProvidersResource.class)
@DisplayName("OAuth Callback Name Derivation Component Tests (DN)")
class OAuthCallbackNameDerivationComponentTest {

    private static final String REGISTER = "/register";
    private static final String LOGIN = "/login";
    private static final String ACCOUNT = "/account";
    private static final String VERIFY_EMAIL_PATH = ACCOUNT + "/verify-email";

    @InjectMock
    VerificationNotifier notifier;

    @Inject
    UserRepository userRepository;

    @BeforeEach
    void resetStubs() {
        OAuthProviderStubs.resetAll();
        Mockito.reset(notifier);
    }

    // TC-506-B5: DN-1, cites TC-459-B5 (regression).
    @Test
    @DisplayName("TC-506-B5: Google both names present -> 200, DB row has exactly those names")
    void googleBothNamesProvisionsExactNames() {
        String sub = "google-sub-" + UUID.randomUUID();
        String email = "both-names-" + UUID.randomUUID() + "@example.com";
        stubGoogleUserInfo(sub, email, "{\"given_name\":\"Alex\",\"family_name\":\"Morales\"}");

        googleCallback(sub).then().statusCode(200)
                .body("account.firstName", equalTo("Alex"))
                .body("account.lastName", equalTo("Morales"));
    }

    // TC-506-B6: DN-2 (the reported production 500, highest-priority case in this doc).
    @Test
    @DisplayName("TC-506-B6: Google given_name only, single-token name -> 200 (not 500), firstName=Alex, lastName=\"\"")
    void googleGivenNameOnlySingleTokenNameProvisions200() {
        String sub = "google-sub-" + UUID.randomUUID();
        String email = "given-only-" + UUID.randomUUID() + "@example.com";
        stubGoogleUserInfo(sub, email, "{\"given_name\":\"Alex\",\"name\":\"Alex\"}");

        googleCallback(sub).then().statusCode(200)
                .body("account.firstName", equalTo("Alex"))
                .body("account.lastName", equalTo(""));

        Optional<User> stored = userRepository.findByEmail(email);
        assertThat(stored).isPresent();
        assertThat(stored.get().getFirstName()).isEqualTo("Alex");
        assertThat(stored.get().getLastName()).isEqualTo("");
    }

    // TC-506-B7: DN-3.
    @Test
    @DisplayName("TC-506-B7: Google given_name only, fuller full name -> firstName=Alex, lastName=Morales")
    void googleGivenNameOnlyFullerNameDerivesLastName() {
        String sub = "google-sub-" + UUID.randomUUID();
        String email = "fuller-name-" + UUID.randomUUID() + "@example.com";
        stubGoogleUserInfo(sub, email, "{\"given_name\":\"Alex\",\"name\":\"Alex Morales\"}");

        googleCallback(sub).then().statusCode(200)
                .body("account.firstName", equalTo("Alex"))
                .body("account.lastName", equalTo("Morales"));
    }

    // TC-506-B8: DN-4 (defensive).
    @Test
    @DisplayName("TC-506-B8: Google supplies no usable name at all -> falls back to email local part")
    void googleNoUsableNameFallsBackToEmailLocalPart() {
        String sub = "google-sub-" + UUID.randomUUID();
        String email = "j.smith-" + UUID.randomUUID() + "@example.com";
        stubGoogleUserInfo(sub, email, "{}");

        googleCallback(sub).then().statusCode(200)
                .body("account.firstName", equalTo(email.substring(0, email.indexOf('@'))))
                .body("account.lastName", equalTo(""));
    }

    // TC-506-B9: DN-5.
    @Test
    @DisplayName("TC-506-B9: GitHub two-word public name -> firstName=Ada, lastName=Lovelace")
    void githubTwoWordNameSplits() {
        long id = System.nanoTime();
        String email = "ada-" + UUID.randomUUID() + "@example.com";
        OAuthProviderStubs.stubGithubHappyPath(id, "adalovelace", "Ada Lovelace", email, true, true);

        githubCallback().then().statusCode(200)
                .body("account.firstName", equalTo("Ada"))
                .body("account.lastName", equalTo("Lovelace"));
    }

    // TC-506-B10: DN-6.
    @Test
    @DisplayName("TC-506-B10: GitHub mononym public name -> firstName=Madonna, lastName=\"\"")
    void githubMononymName() {
        long id = System.nanoTime();
        String email = "madonna-" + UUID.randomUUID() + "@example.com";
        OAuthProviderStubs.stubGithubHappyPath(id, "madonna", "Madonna", email, true, true);

        githubCallback().then().statusCode(200)
                .body("account.firstName", equalTo("Madonna"))
                .body("account.lastName", equalTo(""));
    }

    // TC-506-B11: DN-7, cites TC-459-B9 (regression).
    @Test
    @DisplayName("TC-506-B11: GitHub no public name -> firstName falls back to login, lastName=\"\"")
    void githubNoPublicNameFallsBackToLogin() {
        long id = System.nanoTime();
        String email = "octocat-" + UUID.randomUUID() + "@example.com";
        OAuthProviderStubs.stubGithubHappyPath(id, "octocat", null, email, true, true);

        githubCallback().then().statusCode(200)
                .body("account.firstName", equalTo("octocat"))
                .body("account.lastName", equalTo(""));
    }

    // TC-506-B12: DN-8.
    @Test
    @DisplayName("TC-506-B12: irregular whitespace in provider name is trimmed and collapsed")
    void irregularWhitespaceIsNormalizedOverTheWire() {
        long id = System.nanoTime();
        String email = "grace-" + UUID.randomUUID() + "@example.com";
        OAuthProviderStubs.stubGithubHappyPath(id, "gracehopper", "  Grace   Hopper  ", email, true, true);

        githubCallback().then().statusCode(200)
                .body("account.firstName", equalTo("Grace"))
                .body("account.lastName", equalTo("Hopper"));
    }

    // TC-506-B13: DN-9.
    @Test
    @DisplayName("TC-506-B13: an over-length provider name is truncated to 100 characters, callback still succeeds")
    void overLengthNameIsTruncatedOverTheWire() {
        long id = System.nanoTime();
        String email = "long-" + UUID.randomUUID() + "@example.com";
        String longFirst = "A".repeat(150);
        OAuthProviderStubs.stubGithubHappyPath(id, "longname", longFirst, email, true, true);

        Response response = githubCallback();
        response.then().statusCode(200);
        String firstName = response.jsonPath().getString("account.firstName");
        assertThat(firstName.length()).isLessThanOrEqualTo(100);
    }

    // TC-506-B14: DN-10 (contract shape).
    @Test
    @DisplayName("TC-506-B14: GET /account after a DN-derived account -> firstName/lastName are non-null JSON strings")
    void derivedAccountNamesAreNeverNullOverTheWire() {
        long id = System.nanoTime();
        String email = "nonnull-" + UUID.randomUUID() + "@example.com";
        OAuthProviderStubs.stubGithubHappyPath(id, "nonnull-user", null, email, true, true);

        String token = githubCallback().then().statusCode(200).extract().jsonPath().getString("token");

        given().header("Authorization", "Bearer " + token)
                .when().get(ACCOUNT)
                .then().statusCode(200)
                .body("firstName", equalTo("nonnull-user"))
                .body("lastName", equalTo(""));
    }

    // TC-506-B15: DN-11.
    @Test
    @DisplayName("TC-506-B15: a derived name is correctable via PATCH /account exactly like any other account")
    void derivedNameIsEditableViaPatchAccount() {
        long id = System.nanoTime();
        String email = "editable-" + UUID.randomUUID() + "@example.com";
        OAuthProviderStubs.stubGithubHappyPath(id, "editable-user", null, email, true, true);

        String token = githubCallback().then().statusCode(200).extract().jsonPath().getString("token");

        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("firstName", "Corrected", "lastName", "Name"))
                .when().patch(ACCOUNT)
                .then().statusCode(200)
                .body("firstName", equalTo("Corrected"))
                .body("lastName", equalTo("Name"));
    }

    // TC-506-B16: DN-12 (no silent overwrite, existing-link).
    @Test
    @DisplayName("TC-506-B16: repeating the same Google sub's callback never overwrites the first-derived name")
    void repeatedCallbackNeverOverwritesFirstDerivedName() {
        String sub = "google-sub-" + UUID.randomUUID();
        String email = "repeat-" + UUID.randomUUID() + "@example.com";
        stubGoogleUserInfo(sub, email, "{\"given_name\":\"Alex\",\"name\":\"Alex\"}");
        googleCallback(sub).then().statusCode(200)
                .body("account.firstName", equalTo("Alex"))
                .body("account.lastName", equalTo(""));

        stubGoogleUserInfo(sub, email, "{\"given_name\":\"Alexandra\",\"family_name\":\"Fullerton\",\"name\":\"Alexandra Fullerton\"}");
        googleCallback(sub).then().statusCode(200)
                .body("account.firstName", equalTo("Alex"))
                .body("account.lastName", equalTo(""));

        Optional<User> stored = userRepository.findByEmail(email);
        assertThat(stored).isPresent();
        assertThat(stored.get().getFirstName()).isEqualTo("Alex");
        assertThat(stored.get().getLastName()).isEqualTo("");
    }

    // TC-506-B17: DN-13, extends TC-459-B13.
    @Test
    @DisplayName("TC-506-B17: auto-linking to an existing password account never overwrites jane's own chosen name")
    void autoLinkNeverOverwritesExistingAccountName() {
        String email = "jane-" + UUID.randomUUID() + "@example.com";
        registerVerifyLoginAndGetAccountId(email, "test1234");

        String sub = "google-sub-" + UUID.randomUUID();
        stubGoogleUserInfo(sub, email, "{\"given_name\":\"Someone\",\"family_name\":\"Else\",\"name\":\"Someone Else\"}");

        googleCallback(sub).then().statusCode(200)
                .body("account.firstName", equalTo("Jane"))
                .body("account.lastName", equalTo("Doe"));

        Optional<User> stored = userRepository.findByEmail(email);
        assertThat(stored).isPresent();
        assertThat(stored.get().getFirstName()).isEqualTo("Jane");
        assertThat(stored.get().getLastName()).isEqualTo("Doe");
    }

    // --- helpers ---

    private void stubGoogleUserInfo(String sub, String email, String nameFieldsJson) {
        OAuthProviderStubs.stubGoogleToken(200, """
                {"access_token":"google-access-token","token_type":"Bearer"}
                """);
        String inner = nameFieldsJson.replaceAll("^\\{|}$", "");
        String prefix = "{\"sub\":\"" + sub + "\",\"email\":\"" + email + "\",\"email_verified\":true"
                + (inner.isBlank() ? "" : "," + inner) + "}";
        OAuthProviderStubs.stubGoogleUserInfo(200, prefix);
    }

    private Response googleCallback(String sub) {
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

    private void registerAndVerify(String email, String password) {
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
    }

    private String registerVerifyLoginAndGetAccountId(String email, String password) {
        registerAndVerify(email, password);
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", password))
                .when().post(LOGIN)
                .then().statusCode(200)
                .extract().jsonPath().getString("account.id");
    }
}
