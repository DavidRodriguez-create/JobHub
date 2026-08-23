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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Component tests for {@code POST /account/change-password} with the 2FA guard
 * (ADR 0012, story #133). Covers TC-CHPWD-C01..C07.
 */
@QuarkusTest
@DisplayName("Change Password Component Tests")
class ChangePasswordComponentTest {

    private static final String REGISTER = "/register";
    private static final String LOGIN = "/login";
    private static final String ACCOUNT = "/account";
    private static final String VERIFY_EMAIL_PATH = ACCOUNT + "/verify-email";
    private static final String SETUP_PATH = ACCOUNT + "/2fa/setup";
    private static final String VERIFY_SETUP_PATH = ACCOUNT + "/2fa/verify-setup";
    private static final String CHANGE_PASSWORD_PATH = ACCOUNT + "/change-password";

    @InjectMock
    VerificationNotifier notifier;

    private String uniqueEmail;

    @BeforeEach
    void setUp() {
        Mockito.reset(notifier);
        uniqueEmail = "user-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    @DisplayName("TC-CHPWD-C01: ✓ 2FA user, valid password + valid TOTP code → 204")
    void twoFactorUserWithValidTotpCodeChangesPassword() {
        String token = registerVerifyAndLogin();
        String setupKey = enableTwoFactor(token);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(changePasswordBody("test1234", "newpass1", currentTotpCode(setupKey)))
                .when().post(CHANGE_PASSWORD_PATH)
                .then().statusCode(204);
    }

    @Test
    @DisplayName("TC-CHPWD-C02: ✗ 2FA user, missing TOTP code → 401")
    void twoFactorUserWithMissingTotpCodeReturns401() {
        String token = registerVerifyAndLogin();
        enableTwoFactor(token);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(changePasswordBody("test1234", "newpass1", null))
                .when().post(CHANGE_PASSWORD_PATH)
                .then().statusCode(401);
    }

    @Test
    @DisplayName("TC-CHPWD-C03: ✗ 2FA user, wrong TOTP code → 401")
    void twoFactorUserWithWrongTotpCodeReturns401() {
        String token = registerVerifyAndLogin();
        enableTwoFactor(token);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(changePasswordBody("test1234", "newpass1", "000000"))
                .when().post(CHANGE_PASSWORD_PATH)
                .then().statusCode(401);
    }

    @Test
    @DisplayName("TC-CHPWD-C04: ✓ 2FA user, backup code accepted → 204")
    void twoFactorUserWithBackupCodeChangesPassword() {
        String token = registerVerifyAndLogin();
        String setupKey = setupTwoFactor(token);
        String backupCode = verifySetupReturningFirstBackupCode(token, setupKey);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(changePasswordBody("test1234", "newpass1", backupCode))
                .when().post(CHANGE_PASSWORD_PATH)
                .then().statusCode(204);
    }

    @Test
    @DisplayName("TC-CHPWD-C05: ✓ non-2FA user, no TOTP code needed (regression) → 204")
    void nonTwoFactorUserChangesPasswordWithoutTotpCode() {
        String token = registerVerifyAndLogin();

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(changePasswordBody("test1234", "newpass1", null))
                .when().post(CHANGE_PASSWORD_PATH)
                .then().statusCode(204);
    }

    @Test
    @DisplayName("TC-CHPWD-C06: ✓ non-2FA user with totpCode present: ignored → 204")
    void nonTwoFactorUserWithTotpCodePresentIsIgnored() {
        String token = registerVerifyAndLogin();

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(changePasswordBody("test1234", "newpass1", "999999"))
                .when().post(CHANGE_PASSWORD_PATH)
                .then().statusCode(204);
    }

    @Test
    @DisplayName("TC-CHPWD-C07: ✗ wrong current password → 401")
    void wrongCurrentPasswordReturns401() {
        String token = registerVerifyAndLogin();

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(changePasswordBody("WRONG", "newpass1", null))
                .when().post(CHANGE_PASSWORD_PATH)
                .then().statusCode(401);
    }

    // --- helpers ---

    private Map<String, Object> changePasswordBody(String currentPassword, String newPassword, String totpCode) {
        Map<String, Object> body = new HashMap<>();
        body.put("currentPassword", currentPassword);
        body.put("newPassword", newPassword);
        if (totpCode != null) {
            body.put("totpCode", totpCode);
        }
        return body;
    }

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
