package com.davidcreate.jobhub.auth.component_tests;

import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Component tests for {@code GET /auth/internal/users/{userId}/two-factor} (ADR 0019,
 * story #384/#388). Protected by {@code X-Service-Key}, not a user JWT (mirrors
 * {@code InternalUserEmailResourceComponentTest}).
 *
 * Covers TC-384-B1..B6. Real TOTP fixtures via the register -> verify-email -> login ->
 * setup -> verify-setup chain (same pattern as {@code LoginTwoFactorComponentTest}), not
 * WireMock/stubs -- auth-service is the callee, not a caller, on this path.
 */
@QuarkusTest
@DisplayName("Internal Two-Factor Status Component Tests (TC-384-B1..B6)")
class InternalTwoFactorStatusComponentTest {

    private static final String BASE = "/internal/users";
    private static final String SERVICE_KEY_HEADER = "X-Service-Key";
    private static final String VALID_SERVICE_KEY = "test-internal-key";

    private static final String REGISTER = "/register";
    private static final String LOGIN = "/login";
    private static final String ACCOUNT = "/account";
    private static final String VERIFY_EMAIL_PATH = ACCOUNT + "/verify-email";
    private static final String SETUP_PATH = ACCOUNT + "/2fa/setup";
    private static final String VERIFY_SETUP_PATH = ACCOUNT + "/2fa/verify-setup";

    @InjectMock
    VerificationNotifier notifier;

    private String uniqueEmail;

    @BeforeEach
    void setUp() {
        Mockito.reset(notifier);
        uniqueEmail = "twofactor-status-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    @DisplayName("TC-384-B1: fixture user with 2FA enabled -> 200 { twoFactorEnabled: true }")
    void enrolledUserReturnsTrue() {
        String token = registerVerifyAndLogin();
        enableTwoFactor(token);
        String userId = currentUserId(token);

        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .when().get(BASE + "/" + userId + "/two-factor")
                .then()
                .statusCode(200)
                .body("twoFactorEnabled", equalTo(true));
    }

    @Test
    @DisplayName("TC-384-B2: fixture user with no 2FA -> 200 { twoFactorEnabled: false }")
    void notEnrolledUserReturnsFalse() {
        String token = registerVerifyAndLogin();
        String userId = currentUserId(token);

        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .when().get(BASE + "/" + userId + "/two-factor")
                .then()
                .statusCode(200)
                .body("twoFactorEnabled", equalTo(false));
    }

    @Test
    @DisplayName("TC-384-B3: userId path segment is not a valid UUID -> 400")
    void malformedUserIdReturns400() {
        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .when().get(BASE + "/not-a-uuid/two-factor")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("TC-384-B4: well-formed userId that does not exist -> 404")
    void unknownUserIdReturns404() {
        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .when().get(BASE + "/" + UUID.randomUUID() + "/two-factor")
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("TC-384-B5: missing X-Service-Key -> 401")
    void missingServiceKeyReturns401() {
        given()
                .when().get(BASE + "/" + UUID.randomUUID() + "/two-factor")
                .then()
                .statusCode(401)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    @Test
    @DisplayName("TC-384-B6: wrong X-Service-Key -> 401")
    void wrongServiceKeyReturns401() {
        given()
                .header(SERVICE_KEY_HEADER, "wrong-value")
                .when().get(BASE + "/" + UUID.randomUUID() + "/two-factor")
                .then()
                .statusCode(401)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // --- helpers (mirrors LoginTwoFactorComponentTest) ---

    private void registerOnly(String email, String password) {
        given().contentType(ContentType.JSON)
                .body(Map.of("firstName", "Test", "lastName", "User",
                        "email", email, "password", password))
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

    private String login(String email, String password) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", password))
                .when().post(LOGIN)
                .then().statusCode(200)
                .extract().jsonPath().getString("token");
    }

    private String registerVerifyAndLogin() {
        registerAndVerify(uniqueEmail, "test1234");
        return login(uniqueEmail, "test1234");
    }

    private String setupTwoFactor(String token) {
        return given()
                .header("Authorization", "Bearer " + token)
                .when().post(SETUP_PATH)
                .then().statusCode(200)
                .extract().jsonPath().getString("setupKey");
    }

    private void enableTwoFactor(String token) {
        String setupKey = setupTwoFactor(token);
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("totpCode", currentTotpCode(setupKey)))
                .when().post(VERIFY_SETUP_PATH)
                .then().statusCode(200);
    }

    private String currentUserId(String token) {
        return given()
                .header("Authorization", "Bearer " + token)
                .when().get(ACCOUNT)
                .then().statusCode(200)
                .extract().jsonPath().getString("id");
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
