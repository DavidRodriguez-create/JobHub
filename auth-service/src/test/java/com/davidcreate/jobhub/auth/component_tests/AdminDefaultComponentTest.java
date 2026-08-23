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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Component tests for the admin feature using the default test profile
 * (no auth.admin.emails configured → empty allowlist → no admins).
 *
 * Covers:
 *   A-C-02: login returns isAdmin=false and groups=["user"] for non-allowlist email
 *   A-C-04: GET /auth/account returns isAdmin=false for regular token
 *   A-C-05: empty allowlist yields no admin at login
 *   A-C-09: POST /auth/account/verifications/consume with wrong action → 400
 *
 * A-C-07/A-C-08 (the emailed "admin-trigger" request/consume verification flow) were
 * removed by ADR 0019 (story #384): admin crawl/enrichment triggers are now gated by
 * the admin's own 2FA, not a bespoke emailed code. See InternalTwoFactorStatusComponentTest
 * / InternalTwoFactorVerifyComponentTest for the replacement service-to-service endpoints.
 *
 * Uses the same Quarkus context as other default-profile tests.
 * VerificationNotifier is mocked to capture emailed codes without SMTP.
 */
@QuarkusTest
@DisplayName("Auth Admin Default-Profile Component Tests (A-C-02, A-C-04, A-C-05, A-C-09)")
class AdminDefaultComponentTest {

    private static final String REGISTER = "/register";
    private static final String LOGIN = "/login";
    private static final String ACCOUNT = "/account";
    private static final String VERIFY_EMAIL_PATH = ACCOUNT + "/verify-email";
    private static final String VERIFICATIONS = ACCOUNT + "/verifications";
    private static final String CONSUME = VERIFICATIONS + "/consume";

    @InjectMock
    VerificationNotifier notifier;

    private String uniqueEmail;

    @BeforeEach
    void setUp() {
        Mockito.reset(notifier);
        uniqueEmail = "admin-default-" + UUID.randomUUID() + "@example.com";
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void register(String email) {
        given().contentType("application/json")
                .body(Map.of("firstName", "Test", "lastName", "User", "email", email, "password", "test1234"))
                .when().post(REGISTER)
                .then().statusCode(201);
    }

    private String captureVerifyEmailCode() {
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(notifier).sendActionCode(anyString(), eq(VerificationAction.VERIFY_EMAIL), cap.capture());
        Mockito.reset(notifier);
        return cap.getValue();
    }

    private String registerVerifyAndLogin(String email) {
        register(email);
        String code = captureVerifyEmailCode();
        given().contentType("application/json")
                .body(Map.of("email", email, "code", code))
                .when().post(VERIFY_EMAIL_PATH)
                .then().statusCode(200);
        return given().contentType("application/json")
                .body(Map.of("email", email, "password", "test1234"))
                .when().post(LOGIN)
                .then().statusCode(200)
                .extract().jsonPath().getString("token");
    }

    // ── A-C-02 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A-C-02: login returns isAdmin=false and groups=[user] when email not in allowlist")
    void loginNonAllowlistEmailReturnsAdminFalse() {
        register(uniqueEmail);
        String code = captureVerifyEmailCode();
        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail, "code", code))
                .when().post(VERIFY_EMAIL_PATH)
                .then().statusCode(200);

        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail, "password", "test1234"))
                .when().post(LOGIN)
                .then()
                .statusCode(200)
                .body("account.isAdmin", equalTo(false))
                .body("account.groups", hasItem("user"));
    }

    // ── A-C-04 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A-C-04: GET /auth/account returns isAdmin=false for regular token")
    void getAccountReturnsAdminFalseForRegularToken() {
        String token = registerVerifyAndLogin(uniqueEmail);

        given().header("Authorization", "Bearer " + token)
                .when().get(ACCOUNT)
                .then()
                .statusCode(200)
                .body("isAdmin", equalTo(false));
    }

    // ── A-C-05 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A-C-05: empty allowlist (default profile) yields isAdmin=false for any user")
    void emptyAllowlistYieldsNoAdmin() {
        register(uniqueEmail);
        String code = captureVerifyEmailCode();
        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail, "code", code))
                .when().post(VERIFY_EMAIL_PATH)
                .then().statusCode(200);

        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail, "password", "test1234"))
                .when().post(LOGIN)
                .then()
                .statusCode(200)
                .body("account.isAdmin", equalTo(false))
                .body("account.groups", hasItem("user"));
    }

    // ── A-C-09 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A-C-09: POST /auth/account/verifications/consume with wrong action returns 400")
    void consumeVerificationWrongActionReturns400() {
        String token = registerVerifyAndLogin(uniqueEmail);

        String verificationId = given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("action", "delete-account"))
                .when().post(VERIFICATIONS)
                .then().statusCode(200)
                .extract().jsonPath().getString("verificationId");

        ArgumentCaptor<String> codeCap = ArgumentCaptor.forClass(String.class);
        verify(notifier).sendActionCode(anyString(), eq(VerificationAction.DELETE_ACCOUNT), codeCap.capture());
        String actionCode = codeCap.getValue();

        // consume with wrong action (delete-all-applications instead of delete-account)
        given()
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("verificationId", verificationId, "code", actionCode, "action", "delete-all-applications"))
                .when().post(CONSUME)
                .then()
                .statusCode(400);
    }
}
