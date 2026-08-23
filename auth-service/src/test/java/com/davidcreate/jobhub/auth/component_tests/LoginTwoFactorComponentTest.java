package com.davidcreate.jobhub.auth.component_tests;

import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Component tests for the two-step 2FA login flow: {@code POST /auth/login}
 * (modified) and {@code POST /auth/login/2fa} (new) (ADR 0012, story #133).
 *
 * Covers TC-LOGIN-C01..C03 and TC-LOGIN2-C01..C05.
 */
@QuarkusTest
@DisplayName("Login Two-Factor Component Tests")
class LoginTwoFactorComponentTest {

    private static final String REGISTER = "/register";
    private static final String LOGIN = "/login";
    private static final String LOGIN_2FA = "/login/2fa";
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
        uniqueEmail = "user-" + UUID.randomUUID() + "@example.com";
    }

    @Nested
    @DisplayName("POST /auth/login (modified)")
    class Login {

        @Test
        @DisplayName("TC-LOGIN-C01: ✓ non-2FA login returns token directly (regression)")
        void nonTwoFactorLoginReturnsTokenDirectly() {
            registerAndVerify(uniqueEmail, "test1234");

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
        @DisplayName("TC-LOGIN-C02: ✓ 2FA user login returns challenge, no token")
        void twoFactorUserLoginReturnsChallenge() {
            String token = registerVerifyAndLogin();
            enableTwoFactor(token);

            given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("email", uniqueEmail, "password", "test1234"))
                    .when().post(LOGIN)
                    .then()
                    .statusCode(200)
                    .body("twoFactorRequired", equalTo(true))
                    .body("twoFactorToken", notNullValue())
                    .body("token", nullValue());
        }

        @Test
        @DisplayName("TC-LOGIN-C03: ✗ wrong password returns 401 regardless of 2FA")
        void wrongPasswordReturns401RegardlessOfTwoFactor() {
            String token = registerVerifyAndLogin();
            enableTwoFactor(token);

            given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("email", uniqueEmail, "password", "WRONG"))
                    .when().post(LOGIN)
                    .then().statusCode(401);
        }
    }

    @Nested
    @DisplayName("POST /auth/login/2fa")
    class LoginTwoFactor {

        @Test
        @DisplayName("TC-LOGIN2-C01: ✓ valid challenge + valid TOTP code returns full LoginResponse")
        void validChallengeAndCodeReturnsFullLoginResponse() {
            String token = registerVerifyAndLogin();
            String setupKey = enableTwoFactor(token);

            String challengeToken = beginTwoFactorChallenge();

            given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("twoFactorToken", challengeToken, "totpCode", currentTotpCode(setupKey)))
                    .when().post(LOGIN_2FA)
                    .then()
                    .statusCode(200)
                    .body("token", notNullValue())
                    .body("twoFactorRequired", equalTo(false))
                    .body("account.email", equalTo(uniqueEmail));
        }

        @Test
        @DisplayName("TC-LOGIN2-C02: ✗ wrong TOTP code returns 401, challenge not consumed")
        void wrongCodeReturns401AndChallengeRemainsUsable() {
            String token = registerVerifyAndLogin();
            String setupKey = enableTwoFactor(token);

            String challengeToken = beginTwoFactorChallenge();

            given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("twoFactorToken", challengeToken, "totpCode", "000000"))
                    .when().post(LOGIN_2FA)
                    .then().statusCode(401);

            // challenge still usable: a correct code afterwards succeeds
            given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("twoFactorToken", challengeToken, "totpCode", currentTotpCode(setupKey)))
                    .when().post(LOGIN_2FA)
                    .then().statusCode(200);
        }

        @Test
        @DisplayName("TC-LOGIN2-C03: ✓ backup code accepted in place of TOTP code")
        void backupCodeAcceptedForLogin() {
            String token = registerVerifyAndLogin();
            String setupKey = setupTwoFactor(token);
            String backupCode = verifySetupReturningFirstBackupCode(token, setupKey);

            String challengeToken = beginTwoFactorChallenge();

            given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("twoFactorToken", challengeToken, "totpCode", backupCode))
                    .when().post(LOGIN_2FA)
                    .then()
                    .statusCode(200)
                    .body("token", notNullValue());
        }

        @Test
        @DisplayName("TC-LOGIN2-C04: ✗ expired challenge returns 400")
        void expiredChallengeReturns400() {
            given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("twoFactorToken", UUID.randomUUID().toString(), "totpCode", "123456"))
                    .when().post(LOGIN_2FA)
                    .then().statusCode(400);
        }

        @Test
        @DisplayName("TC-LOGIN2-C05: ✗ consumed challenge returns 400")
        void consumedChallengeReturns400() {
            String token = registerVerifyAndLogin();
            String setupKey = enableTwoFactor(token);

            String challengeToken = beginTwoFactorChallenge();

            given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("twoFactorToken", challengeToken, "totpCode", currentTotpCode(setupKey)))
                    .when().post(LOGIN_2FA)
                    .then().statusCode(200);

            given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("twoFactorToken", challengeToken, "totpCode", currentTotpCode(setupKey)))
                    .when().post(LOGIN_2FA)
                    .then().statusCode(400);
        }

        @Test
        @DisplayName("✗ malformed challenge token returns 400")
        void malformedChallengeTokenReturns400() {
            given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("twoFactorToken", "not-a-uuid", "totpCode", "123456"))
                    .when().post(LOGIN_2FA)
                    .then().statusCode(400);
        }
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

    private String beginTwoFactorChallenge() {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", uniqueEmail, "password", "test1234"))
                .when().post(LOGIN)
                .then().statusCode(200)
                .extract().jsonPath().getString("twoFactorToken");
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
