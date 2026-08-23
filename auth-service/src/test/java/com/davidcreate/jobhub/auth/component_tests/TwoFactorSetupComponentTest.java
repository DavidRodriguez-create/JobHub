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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Component tests for the TOTP 2FA enable flow: {@code POST /account/2fa/setup}
 * and {@code POST /account/2fa/verify-setup} (ADR 0012, story #133).
 *
 * Covers TC-SETUP-C01..C05.
 */
@QuarkusTest
@DisplayName("Two-Factor Setup Component Tests")
class TwoFactorSetupComponentTest {

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
        uniqueEmail = "user-" + UUID.randomUUID() + "@example.com";
    }

    @Nested
    @DisplayName("POST /auth/account/2fa/setup")
    class Setup {

        @Test
        @DisplayName("TC-SETUP-C01: ✓ returns otpauthUri and setupKey")
        void setupReturnsOtpauthUriAndSetupKey() {
            String token = registerVerifyAndLogin();

            given()
                    .header("Authorization", "Bearer " + token)
                    .when().post(SETUP_PATH)
                    .then()
                    .statusCode(200)
                    .body("otpauthUri", notNullValue())
                    .body("setupKey", notNullValue());
        }

        @Test
        @DisplayName("TC-SETUP-C02: ✗ already enabled → 409")
        void setupWhenAlreadyEnabledReturns409() {
            String token = registerVerifyAndLogin();
            enableTwoFactor(token);

            given()
                    .header("Authorization", "Bearer " + token)
                    .when().post(SETUP_PATH)
                    .then().statusCode(409);
        }

        @Test
        @DisplayName("✗ no token → 401")
        void setupUnauthorized() {
            given().when().post(SETUP_PATH).then().statusCode(401);
        }

        @Test
        @DisplayName("TC-SETUP-C05: ✓ abandoned setup retried succeeds with a new QR/secret")
        void setupRetryAfterAbandonedSetupSucceeds() {
            String token = registerVerifyAndLogin();

            String firstKey = given()
                    .header("Authorization", "Bearer " + token)
                    .when().post(SETUP_PATH)
                    .then().statusCode(200)
                    .extract().jsonPath().getString("setupKey");

            String secondKey = given()
                    .header("Authorization", "Bearer " + token)
                    .when().post(SETUP_PATH)
                    .then().statusCode(200)
                    .extract().jsonPath().getString("setupKey");

            org.assertj.core.api.Assertions.assertThat(secondKey).isNotEqualTo(firstKey);

            verifySetupWithValidCode(token, secondKey)
                    .then().statusCode(200)
                    .body("backupCodes", hasSize(8));
        }
    }

    @Nested
    @DisplayName("POST /auth/account/2fa/verify-setup")
    class VerifySetup {

        @Test
        @DisplayName("TC-SETUP-C03: ✓ valid code → 200 + 8 backup codes, enables 2FA")
        void verifySetupWithValidCodeEnablesTwoFactor() {
            String token = registerVerifyAndLogin();
            String setupKey = setupTwoFactor(token);

            verifySetupWithValidCode(token, setupKey)
                    .then()
                    .statusCode(200)
                    .body("backupCodes", hasSize(8));

            given()
                    .header("Authorization", "Bearer " + token)
                    .when().get(ACCOUNT)
                    .then()
                    .statusCode(200)
                    .body("twoFactorEnabled", equalTo(true));
        }

        @Test
        @DisplayName("TC-SETUP-C04: ✗ wrong code → 400")
        void verifySetupWithWrongCodeReturns400() {
            String token = registerVerifyAndLogin();
            setupTwoFactor(token);

            given()
                    .header("Authorization", "Bearer " + token)
                    .contentType(ContentType.JSON)
                    .body(Map.of("totpCode", "000000"))
                    .when().post(VERIFY_SETUP_PATH)
                    .then().statusCode(400);
        }

        @Test
        @DisplayName("✗ no pending setup → 400")
        void verifySetupWithoutPendingSetupReturns400() {
            String token = registerVerifyAndLogin();

            given()
                    .header("Authorization", "Bearer " + token)
                    .contentType(ContentType.JSON)
                    .body(Map.of("totpCode", "123456"))
                    .when().post(VERIFY_SETUP_PATH)
                    .then().statusCode(400);
        }

        @Test
        @DisplayName("✗ no token → 401")
        void verifySetupUnauthorized() {
            given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("totpCode", "123456"))
                    .when().post(VERIFY_SETUP_PATH)
                    .then().statusCode(401);
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

    private io.restassured.response.Response verifySetupWithValidCode(String token, String setupKey) {
        String code = currentTotpCode(setupKey);
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("totpCode", code))
                .when().post(VERIFY_SETUP_PATH);
    }

    private void enableTwoFactor(String token) {
        String setupKey = setupTwoFactor(token);
        verifySetupWithValidCode(token, setupKey).then().statusCode(200);
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
