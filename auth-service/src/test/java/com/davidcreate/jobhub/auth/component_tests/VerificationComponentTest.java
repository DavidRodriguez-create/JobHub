package com.davidcreate.jobhub.auth.component_tests;

import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
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
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Email-verification and destructive-action flows.
 * {@link VerificationNotifier} is mocked so tests capture the emailed code.
 * No @Nested + @TestHTTPEndpoint per CLAUDE.md rules.
 *
 * EV-C-01: register → 201 + RegisterResponse{account, verificationRequired:true}
 * EV-C-02: code emailed is 6-digit (not UUID)
 * EV-C-03: verify happy → 200 + AccountResponse with emailVerified=true
 * EV-C-04: login before verify → 403 EmailNotVerified
 * EV-C-05: login wrong password → 401 (not 403 — no leak)
 * EV-C-06: verify consumed code → 400
 * EV-C-07: verify wrong code → 400
 * EV-C-08: verify unknown email → 400 (anti-enum: same 400 as wrong code)
 * EV-C-09: verify malformed email / missing field → 400
 * EV-C-10: verify missing code field → 400
 * EV-C-11: login after verify → 200 + token
 * EV-C-12: resend unknown email → 204, no dispatch
 * EV-C-13: resend verified → 204, no dispatch
 * EV-C-14: resend unverified → 204 + fresh code dispatched
 * EV-C-15: resend malformed email → 400
 * EV-C-16: old code invalid after resend → 400
 * (destructive action flows retained from prior VerificationComponentTest)
 */
@QuarkusTest
@DisplayName("Verification Flow Component Tests — EV-C-01..16")
class VerificationComponentTest {

    private static final String REGISTER = "/register";
    private static final String LOGIN = "/login";
    private static final String ACCOUNT = "/account";
    private static final String VERIFY_EMAIL = ACCOUNT + "/verify-email";
    private static final String RESEND = ACCOUNT + "/resend-verification";

    @InjectMock
    VerificationNotifier notifier;

    private String uniqueEmail;

    @BeforeEach
    void setUp() {
        Mockito.reset(notifier);
        uniqueEmail = "ev-" + UUID.randomUUID() + "@example.com";
    }

    // EV-C-01
    @Test
    @DisplayName("EV-C-01: register → 201 + RegisterResponse{account, verificationRequired:true}")
    void registerReturns201WithVerificationRequired() {
        given()
                .contentType("application/json")
                .body(Map.of("firstName", "Test", "lastName", "User",
                        "email", uniqueEmail, "password", "test1234"))
                .when().post(REGISTER)
                .then()
                .statusCode(201)
                .body("account.email", equalTo(uniqueEmail))
                .body("account.emailVerified", equalTo(false))
                .body("verificationRequired", equalTo(true));
    }

    // EV-C-02
    @Test
    @DisplayName("EV-C-02: code emailed is exactly 6 digits, not a UUID")
    void registrationEmailsA6DigitCode() {
        register(uniqueEmail);

        ArgumentCaptor<String> codeCap = ArgumentCaptor.forClass(String.class);
        verify(notifier).sendActionCode(anyString(), eq(VerificationAction.VERIFY_EMAIL), codeCap.capture());
        String code = codeCap.getValue();

        assertThat(code).matches("\\d{6}");
        assertThat(code).doesNotContain("-"); // not a UUID
    }

    // EV-C-03
    @Test
    @DisplayName("EV-C-03: verify happy path → 200 + AccountResponse with emailVerified=true")
    void verifyEmailHappyPath() {
        register(uniqueEmail);
        String code = captureVerifyEmailCode();

        given()
                .contentType("application/json")
                .body(Map.of("email", uniqueEmail, "code", code))
                .when().post(VERIFY_EMAIL)
                .then()
                .statusCode(200)
                .body("email", equalTo(uniqueEmail))
                .body("emailVerified", equalTo(true))
                .body("id", notNullValue());
    }

    // EV-C-04
    @Test
    @DisplayName("EV-C-04: login before verify → 403 with 'Email Not Verified'")
    void loginBeforeVerifyReturns403() {
        register(uniqueEmail);

        given()
                .contentType("application/json")
                .body(Map.of("email", uniqueEmail, "password", "test1234"))
                .when().post(LOGIN)
                .then()
                .statusCode(403)
                .body("error", equalTo("Email Not Verified"));
    }

    // EV-C-05
    @Test
    @DisplayName("EV-C-05: login wrong password → 401 (not 403, even for unverified account)")
    void loginWrongPasswordAlways401() {
        register(uniqueEmail);

        given()
                .contentType("application/json")
                .body(Map.of("email", uniqueEmail, "password", "wrongpass"))
                .when().post(LOGIN)
                .then()
                .statusCode(401);
    }

    // EV-C-06
    @Test
    @DisplayName("EV-C-06: verify with already-consumed code → 400")
    void verifyConsumedCode() {
        register(uniqueEmail);
        String code = captureVerifyEmailCode();

        // First use — consumes it
        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail, "code", code))
                .when().post(VERIFY_EMAIL)
                .then().statusCode(200);

        // Second use → 400
        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail, "code", code))
                .when().post(VERIFY_EMAIL)
                .then().statusCode(400);
    }

    // EV-C-07
    @Test
    @DisplayName("EV-C-07: verify wrong code → 400")
    void verifyWrongCode() {
        register(uniqueEmail);
        String code = captureVerifyEmailCode();
        String wrong = code.equals("000000") ? "111111" : "000000";

        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail, "code", wrong))
                .when().post(VERIFY_EMAIL)
                .then().statusCode(400);
    }

    // EV-C-08: unknown email returns same 400 as wrong code (anti-enumeration)
    @Test
    @DisplayName("EV-C-08: verify unknown email → 400 (anti-enumeration, same as wrong code)")
    void verifyUnknownEmail() {
        given().contentType("application/json")
                .body(Map.of("email", "nobody-" + UUID.randomUUID() + "@example.com", "code", "123456"))
                .when().post(VERIFY_EMAIL)
                .then().statusCode(400);
    }

    // EV-C-09
    @Test
    @DisplayName("EV-C-09: verify malformed email → 400")
    void verifyMalformedEmail() {
        given().contentType("application/json")
                .body(Map.of("email", "not-an-email", "code", "123456"))
                .when().post(VERIFY_EMAIL)
                .then().statusCode(400);
    }

    // EV-C-10
    @Test
    @DisplayName("EV-C-10: verify missing code field → 400")
    void verifyMissingCode() {
        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail))
                .when().post(VERIFY_EMAIL)
                .then().statusCode(400);
    }

    // EV-C-11
    @Test
    @DisplayName("EV-C-11: login after verify → 200 + token")
    void loginAfterVerifySucceeds() {
        register(uniqueEmail);
        String code = captureVerifyEmailCode();

        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail, "code", code))
                .when().post(VERIFY_EMAIL)
                .then().statusCode(200);

        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail, "password", "test1234"))
                .when().post(LOGIN)
                .then()
                .statusCode(200)
                .body("token", notNullValue());
    }

    // EV-C-12
    @Test
    @DisplayName("EV-C-12: resend unknown email → 204, no dispatch (anti-enumeration)")
    void resendUnknownEmail() {
        given().contentType("application/json")
                .body(Map.of("email", "nobody-" + UUID.randomUUID() + "@example.com"))
                .when().post(RESEND)
                .then().statusCode(204);

        verify(notifier, never()).sendActionCode(anyString(), eq(VerificationAction.VERIFY_EMAIL), anyString());
    }

    // EV-C-13
    @Test
    @DisplayName("EV-C-13: resend verified account → 204, no dispatch (anti-enumeration)")
    void resendVerifiedAccount() {
        register(uniqueEmail);
        String code = captureVerifyEmailCode();

        // Verify first
        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail, "code", code))
                .when().post(VERIFY_EMAIL)
                .then().statusCode(200);

        Mockito.reset(notifier);

        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail))
                .when().post(RESEND)
                .then().statusCode(204);

        verify(notifier, never()).sendActionCode(anyString(), eq(VerificationAction.VERIFY_EMAIL), anyString());
    }

    // EV-C-14
    @Test
    @DisplayName("EV-C-14: resend unverified → 204 + fresh code dispatched")
    void resendUnverifiedDispatchesFreshCode() {
        register(uniqueEmail);
        String originalCode = captureVerifyEmailCode();

        Mockito.reset(notifier);

        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail))
                .when().post(RESEND)
                .then().statusCode(204);

        ArgumentCaptor<String> freshCodeCap = ArgumentCaptor.forClass(String.class);
        verify(notifier).sendActionCode(eq(uniqueEmail), eq(VerificationAction.VERIFY_EMAIL), freshCodeCap.capture());
        assertThat(freshCodeCap.getValue()).matches("\\d{6}");
    }

    // EV-C-15
    @Test
    @DisplayName("EV-C-15: resend malformed email → 400")
    void resendMalformedEmail() {
        given().contentType("application/json")
                .body(Map.of("email", "not-an-email"))
                .when().post(RESEND)
                .then().statusCode(400);
    }

    // EV-C-16
    @Test
    @DisplayName("EV-C-16: old code invalid after resend → 400")
    void oldCodeInvalidAfterResend() {
        register(uniqueEmail);
        String oldCode = captureVerifyEmailCode();

        // Resend invalidates prior codes
        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail))
                .when().post(RESEND)
                .then().statusCode(204);

        // Old code no longer works
        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail, "code", oldCode))
                .when().post(VERIFY_EMAIL)
                .then().statusCode(400);
    }

    // Destructive action flows retained
    @Test
    @DisplayName("verifications issues a code; delete-account consumes it and removes the user")
    void deleteAccountFlow() {
        register(uniqueEmail);
        String code = captureVerifyEmailCode();
        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail, "code", code))
                .when().post(VERIFY_EMAIL).then().statusCode(200);

        String jwt = loginVerified(uniqueEmail);

        Mockito.reset(notifier);
        String verificationId = given()
                .header("Authorization", "Bearer " + jwt)
                .contentType("application/json")
                .body(Map.of("action", "delete-account"))
                .when().post(ACCOUNT + "/verifications")
                .then().statusCode(200)
                .body("verificationId", notNullValue())
                .extract().jsonPath().getString("verificationId");

        ArgumentCaptor<String> codeCap = ArgumentCaptor.forClass(String.class);
        verify(notifier).sendActionCode(anyString(), eq(VerificationAction.DELETE_ACCOUNT), codeCap.capture());

        given().header("Authorization", "Bearer " + jwt)
                .contentType("application/json")
                .body(Map.of("verificationId", verificationId, "code", codeCap.getValue()))
                .when().delete(ACCOUNT)
                .then().statusCode(204);

        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail, "password", "test1234"))
                .when().post(LOGIN)
                .then().statusCode(401);
    }

    @Test
    @DisplayName("delete-account rejects a wrong code → 400")
    void deleteAccountWrongCode() {
        register(uniqueEmail);
        String code = captureVerifyEmailCode();
        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail, "code", code))
                .when().post(VERIFY_EMAIL).then().statusCode(200);

        String jwt = loginVerified(uniqueEmail);
        Mockito.reset(notifier);

        String verificationId = given()
                .header("Authorization", "Bearer " + jwt)
                .contentType("application/json")
                .body(Map.of("action", "delete-account"))
                .when().post(ACCOUNT + "/verifications")
                .then().statusCode(200)
                .extract().jsonPath().getString("verificationId");

        ArgumentCaptor<String> codeCap = ArgumentCaptor.forClass(String.class);
        verify(notifier).sendActionCode(anyString(), eq(VerificationAction.DELETE_ACCOUNT), codeCap.capture());
        String wrong = codeCap.getValue().equals("000000") ? "111111" : "000000";

        given().header("Authorization", "Bearer " + jwt)
                .contentType("application/json")
                .body(Map.of("verificationId", verificationId, "code", wrong))
                .when().delete(ACCOUNT)
                .then().statusCode(400);
    }

    @Test
    @DisplayName("verifications/consume validates delete-all-applications code, rejects reuse")
    void consumeDeleteAllApplicationsFlow() {
        register(uniqueEmail);
        String evCode = captureVerifyEmailCode();
        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail, "code", evCode))
                .when().post(VERIFY_EMAIL).then().statusCode(200);

        String jwt = loginVerified(uniqueEmail);
        Mockito.reset(notifier);

        String verificationId = given()
                .header("Authorization", "Bearer " + jwt)
                .contentType("application/json")
                .body(Map.of("action", "delete-all-applications"))
                .when().post(ACCOUNT + "/verifications")
                .then().statusCode(200)
                .extract().jsonPath().getString("verificationId");

        ArgumentCaptor<String> codeCap = ArgumentCaptor.forClass(String.class);
        verify(notifier).sendActionCode(anyString(), eq(VerificationAction.DELETE_ALL_APPLICATIONS), codeCap.capture());
        String actionCode = codeCap.getValue();

        var body = Map.of("verificationId", verificationId, "code", actionCode, "action", "delete-all-applications");

        given().header("Authorization", "Bearer " + jwt)
                .contentType("application/json").body(body)
                .when().post(ACCOUNT + "/verifications/consume")
                .then().statusCode(204);

        given().header("Authorization", "Bearer " + jwt)
                .contentType("application/json").body(body)
                .when().post(ACCOUNT + "/verifications/consume")
                .then().statusCode(400);
    }

    // --- helpers ---

    private void register(String email) {
        given().contentType("application/json")
                .body(Map.of("firstName", "Test", "lastName", "User", "email", email, "password", "test1234"))
                .when().post(REGISTER)
                .then().statusCode(201);
    }

    private String captureVerifyEmailCode() {
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(notifier).sendActionCode(anyString(), eq(VerificationAction.VERIFY_EMAIL), cap.capture());
        return cap.getValue();
    }

    private String loginVerified(String email) {
        return given().contentType("application/json")
                .body(Map.of("email", email, "password", "test1234"))
                .when().post(LOGIN)
                .then().statusCode(200)
                .extract().jsonPath().getString("token");
    }
}
