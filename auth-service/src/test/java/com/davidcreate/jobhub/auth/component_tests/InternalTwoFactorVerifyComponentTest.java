package com.davidcreate.jobhub.auth.component_tests;

import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Component tests for {@code POST /auth/internal/two-factor/verify} (ADR 0019,
 * story #384/#388). Protected by {@code X-Service-Key}, not a user JWT.
 *
 * Covers TC-384-B7..B20. Real TOTP fixtures via the register -> verify-email -> login ->
 * setup -> verify-setup chain (same pattern as {@code LoginTwoFactorComponentTest}), not
 * WireMock/stubs -- auth-service is the callee, not a caller, on this path.
 */
@QuarkusTest
@DisplayName("Internal Two-Factor Verify Component Tests (TC-384-B7..B20)")
class InternalTwoFactorVerifyComponentTest {

    private static final String VERIFY_PATH = "/internal/two-factor/verify";
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
        uniqueEmail = "twofactor-verify-" + UUID.randomUUID() + "@example.com";
    }

    @AfterEach
    void noEmailSentByInternalVerifyEndpoint() {
        // TC-384-B20 / AC-28: the internal verify endpoint never sends an email as a
        // side effect, closing the loop that admin-trigger's removal really did remove
        // the only email-sending path this story touches. Fixture setup (register/verify
        // email) resets the mock after capturing its own code, so any surviving
        // interaction here can only have come from the endpoint under test.
        Mockito.verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("TC-384-B7: no-2FA user, no code -> 200 { outcome: not_enrolled }")
    void notEnrolledNoCode() {
        String token = registerVerifyAndLogin();
        String userId = currentUserId(token);

        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .contentType(ContentType.JSON)
                .body(Map.of("userId", userId))
                .when().post(VERIFY_PATH)
                .then()
                .statusCode(200)
                .body("outcome", equalTo("not_enrolled"));
    }

    @Test
    @DisplayName("TC-384-B8: no-2FA user, arbitrary well-formed code -> 200 { outcome: not_enrolled } (ignored end-to-end)")
    void notEnrolledArbitraryCodeIgnored() {
        String token = registerVerifyAndLogin();
        String userId = currentUserId(token);

        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .contentType(ContentType.JSON)
                .body(Map.of("userId", userId, "code", "999999"))
                .when().post(VERIFY_PATH)
                .then()
                .statusCode(200)
                .body("outcome", equalTo("not_enrolled"));
    }

    @Test
    @DisplayName("TC-384-B9: enrolled user, valid TOTP -> 200 { outcome: verified }")
    void enrolledValidTotpVerified() {
        String token = registerVerifyAndLogin();
        String setupKey = enableTwoFactor(token);
        String userId = currentUserId(token);

        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .contentType(ContentType.JSON)
                .body(Map.of("userId", userId, "code", currentTotpCode(setupKey)))
                .when().post(VERIFY_PATH)
                .then()
                .statusCode(200)
                .body("outcome", equalTo("verified"));
    }

    @Test
    @DisplayName("TC-384-B10: same still-valid TOTP code reused -> 200 { outcome: verified } again (no side effect)")
    void reusableTotpVerifiedAgain() {
        String token = registerVerifyAndLogin();
        String setupKey = enableTwoFactor(token);
        String userId = currentUserId(token);
        String code = currentTotpCode(setupKey);

        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .contentType(ContentType.JSON)
                .body(Map.of("userId", userId, "code", code))
                .when().post(VERIFY_PATH)
                .then().statusCode(200).body("outcome", equalTo("verified"));

        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .contentType(ContentType.JSON)
                .body(Map.of("userId", userId, "code", code))
                .when().post(VERIFY_PATH)
                .then().statusCode(200).body("outcome", equalTo("verified"));
    }

    @Test
    @DisplayName("TC-384-B11: enrolled user, unused backup code -> 200 { outcome: verified }")
    void unusedBackupCodeVerified() {
        String token = registerVerifyAndLogin();
        String setupKey = setupTwoFactor(token);
        String backupCode = verifySetupReturningBackupCodes(token, setupKey).get(0);
        String userId = currentUserId(token);

        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .contentType(ContentType.JSON)
                .body(Map.of("userId", userId, "code", backupCode))
                .when().post(VERIFY_PATH)
                .then()
                .statusCode(200)
                .body("outcome", equalTo("verified"));
    }

    @Test
    @DisplayName("TC-384-B12: same backup code reused -> 422 (single-use, end-to-end)")
    void reusedBackupCodeRejected() {
        String token = registerVerifyAndLogin();
        String setupKey = setupTwoFactor(token);
        String backupCode = verifySetupReturningBackupCodes(token, setupKey).get(0);
        String userId = currentUserId(token);

        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .contentType(ContentType.JSON)
                .body(Map.of("userId", userId, "code", backupCode))
                .when().post(VERIFY_PATH)
                .then().statusCode(200).body("outcome", equalTo("verified"));

        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .contentType(ContentType.JSON)
                .body(Map.of("userId", userId, "code", backupCode))
                .when().post(VERIFY_PATH)
                .then().statusCode(422);
    }

    @Test
    @DisplayName("TC-384-B13: enrolled user, no code field -> 422")
    void enrolledNoCodeReturns422() {
        String token = registerVerifyAndLogin();
        enableTwoFactor(token);
        String userId = currentUserId(token);

        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .contentType(ContentType.JSON)
                .body(Map.of("userId", userId))
                .when().post(VERIFY_PATH)
                .then().statusCode(422);
    }

    @Test
    @DisplayName("TC-384-B14: enrolled user, well-formed but wrong 6-digit code -> 422")
    void enrolledWrongCodeReturns422() {
        String token = registerVerifyAndLogin();
        String setupKey = enableTwoFactor(token);
        String userId = currentUserId(token);
        String wrongCode = wrongTotpCode(setupKey);

        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .contentType(ContentType.JSON)
                .body(Map.of("userId", userId, "code", wrongCode))
                .when().post(VERIFY_PATH)
                .then().statusCode(422);
    }

    @Test
    @DisplayName("TC-384-B15: code matches neither pattern -> 400 (Bean Validation)")
    void malformedCodePatternReturns400() {
        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .contentType(ContentType.JSON)
                .body(Map.of("userId", UUID.randomUUID().toString(), "code", "abc"))
                .when().post(VERIFY_PATH)
                .then().statusCode(400);
    }

    @Test
    @DisplayName("TC-384-B16: no userId field at all -> 400")
    void missingUserIdReturns400() {
        Map<String, Object> body = new HashMap<>();
        body.put("code", "123456");

        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .contentType(ContentType.JSON)
                .body(body)
                .when().post(VERIFY_PATH)
                .then().statusCode(400);
    }

    @Test
    @DisplayName("TC-384-B17: well-formed userId that does not exist -> 404")
    void unknownUserIdReturns404() {
        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .contentType(ContentType.JSON)
                .body(Map.of("userId", UUID.randomUUID().toString()))
                .when().post(VERIFY_PATH)
                .then().statusCode(404);
    }

    @Test
    @DisplayName("TC-384-B18a: missing X-Service-Key -> 401")
    void missingServiceKeyReturns401() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("userId", UUID.randomUUID().toString()))
                .when().post(VERIFY_PATH)
                .then()
                .statusCode(401)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    @Test
    @DisplayName("TC-384-B18b: wrong X-Service-Key -> 401")
    void wrongServiceKeyReturns401() {
        given()
                .header(SERVICE_KEY_HEADER, "wrong-value")
                .contentType(ContentType.JSON)
                .body(Map.of("userId", UUID.randomUUID().toString()))
                .when().post(VERIFY_PATH)
                .then()
                .statusCode(401)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    @Test
    @DisplayName("TC-384-B19: throttle after several consecutive failed-code POSTs -> 429 even with an otherwise-valid code")
    void throttleAfterConsecutiveFailures() {
        String token = registerVerifyAndLogin();
        String setupKey = enableTwoFactor(token);
        String userId = currentUserId(token);
        String wrongCode = wrongTotpCode(setupKey);

        // Default auth.two-factor.verify-max-attempts is 5: five wrong POSTs consume
        // the allowance (each 422 in its own right), the sixth is throttled.
        for (int i = 0; i < 5; i++) {
            given()
                    .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                    .contentType(ContentType.JSON)
                    .body(Map.of("userId", userId, "code", wrongCode))
                    .when().post(VERIFY_PATH)
                    .then().statusCode(422);
        }

        given()
                .header(SERVICE_KEY_HEADER, VALID_SERVICE_KEY)
                .contentType(ContentType.JSON)
                .body(Map.of("userId", userId, "code", currentTotpCode(setupKey)))
                .when().post(VERIFY_PATH)
                .then().statusCode(429);
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

    private List<String> verifySetupReturningBackupCodes(String token, String setupKey) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("totpCode", currentTotpCode(setupKey)))
                .when().post(VERIFY_SETUP_PATH)
                .then().statusCode(200)
                .extract().jsonPath().getList("backupCodes", String.class);
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

    /** A well-formed 6-digit code guaranteed not to match the current TOTP window. */
    private String wrongTotpCode(String setupKey) {
        String correct = currentTotpCode(setupKey);
        int asInt = Integer.parseInt(correct);
        int wrong = (asInt + 500_000) % 1_000_000;
        return String.format("%06d", wrong);
    }
}
