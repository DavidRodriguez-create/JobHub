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
 * Component tests for {@code POST /account/2fa/disable} (ADR 0012, story #133).
 * Covers TC-DIS-C01..C05.
 */
@QuarkusTest
@DisplayName("Disable Two-Factor Component Tests")
class DisableTwoFactorComponentTest {

    private static final String REGISTER = "/register";
    private static final String LOGIN = "/login";
    private static final String ACCOUNT = "/account";
    private static final String VERIFY_EMAIL_PATH = ACCOUNT + "/verify-email";
    private static final String SETUP_PATH = ACCOUNT + "/2fa/setup";
    private static final String VERIFY_SETUP_PATH = ACCOUNT + "/2fa/verify-setup";
    private static final String DISABLE_PATH = ACCOUNT + "/2fa/disable";

    @InjectMock
    VerificationNotifier notifier;

    private String uniqueEmail;

    @BeforeEach
    void setUp() {
        Mockito.reset(notifier);
        uniqueEmail = "user-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    @DisplayName("TC-DIS-C01: ✓ valid TOTP code disables 2FA → 204")
    void validTotpCodeDisablesTwoFactor() {
        String token = registerVerifyAndLogin();
        String setupKey = enableTwoFactor(token);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("totpCode", currentTotpCode(setupKey)))
                .when().post(DISABLE_PATH)
                .then().statusCode(204);
    }

    @Test
    @DisplayName("TC-DIS-C02: ✗ wrong TOTP code rejected → 401")
    void wrongTotpCodeRejected() {
        String token = registerVerifyAndLogin();
        enableTwoFactor(token);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("totpCode", "000000"))
                .when().post(DISABLE_PATH)
                .then().statusCode(401);
    }

    @Test
    @DisplayName("TC-DIS-C03: ✓ backup code accepted to disable → 204")
    void backupCodeAcceptedToDisable() {
        String token = registerVerifyAndLogin();
        String setupKey = setupTwoFactor(token);
        String backupCode = verifySetupReturningFirstBackupCode(token, setupKey);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("totpCode", backupCode))
                .when().post(DISABLE_PATH)
                .then().statusCode(204);
    }

    @Test
    @DisplayName("TC-DIS-C04: ✓ after disable, login is single-step again")
    void afterDisableLoginIsSingleStepAgain() {
        String token = registerVerifyAndLogin();
        String setupKey = enableTwoFactor(token);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("totpCode", currentTotpCode(setupKey)))
                .when().post(DISABLE_PATH)
                .then().statusCode(204);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", uniqueEmail, "password", "test1234"))
                .when().post(LOGIN)
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("twoFactorRequired", equalTo(false));
    }

    @Test
    @DisplayName("TC-DIS-C05: ✗ disable when not enabled → 400")
    void disableWhenNotEnabledReturns400() {
        String token = registerVerifyAndLogin();

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("totpCode", "123456"))
                .when().post(DISABLE_PATH)
                .then().statusCode(400);
    }

    @Test
    @DisplayName("✗ no token → 401")
    void disableUnauthorized() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("totpCode", "123456"))
                .when().post(DISABLE_PATH)
                .then().statusCode(401);
    }

    // --- helpers ---

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

    private String enableTwoFactor(String token) {
        String setupKey = setupTwoFactor(token);
        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("totpCode", currentTotpCode(setupKey)))
                .when().post(VERIFY_SETUP_PATH)
                .then().statusCode(200);
        return setupKey;
    }

    private String verifySetupReturningFirstBackupCode(String token, String setupKey) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("totpCode", currentTotpCode(setupKey)))
                .when().post(VERIFY_SETUP_PATH)
                .then().statusCode(200)
                .extract().jsonPath().getString("backupCodes[0]");
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
