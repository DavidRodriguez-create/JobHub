package com.davidcreate.jobhub.auth.component_tests;

import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Component tests exercising the admin allowlist (AdminAllowlistProfile).
 * Covers A-C-01, A-C-03, A-C-06.
 *
 * Three distinct emails are in the allowlist so each test can register its
 * own user without collision:
 *   admin-story7@example.com  → A-C-01 (straight match)
 *   admin-story7b@example.com → A-C-03 (GET /account with admin token)
 *   admin@example.com         → A-C-06 (case-insensitive via "Admin@Example.COM")
 *
 * Separate top-level class from AdminDefaultComponentTest because @TestProfile
 * forces a new Quarkus application context.
 */
@QuarkusTest
@TestProfile(AdminAllowlistComponentTest.AdminAllowlistProfile.class)
@DisplayName("Auth Admin Allowlist Component Tests — A-C-01, A-C-03, A-C-06")
class AdminAllowlistComponentTest {

    public static class AdminAllowlistProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // Covers all three distinct test emails; includes one uppercase variant for A-C-06.
            return Map.of("auth.admin.emails",
                    "admin-story7@example.com,admin-story7b@example.com,Admin@Example.COM");
        }
    }

    private static final String REGISTER = "/register";
    private static final String LOGIN = "/login";
    private static final String ACCOUNT = "/account";
    private static final String VERIFY_EMAIL = ACCOUNT + "/verify-email";

    @InjectMock
    VerificationNotifier notifier;

    @BeforeEach
    void setUp() {
        Mockito.reset(notifier);
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
                .when().post(VERIFY_EMAIL)
                .then().statusCode(200);
        return given().contentType("application/json")
                .body(Map.of("email", email, "password", "test1234"))
                .when().post(LOGIN)
                .then().statusCode(200)
                .extract().jsonPath().getString("token");
    }

    // ── A-C-01 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A-C-01: login with allowlist email returns isAdmin=true and groups=[user,admin]")
    void loginAllowlistEmailReturnsAdminTrue() {
        String email = "admin-story7@example.com";
        register(email);
        String code = captureVerifyEmailCode();
        given().contentType("application/json")
                .body(Map.of("email", email, "code", code))
                .when().post(VERIFY_EMAIL)
                .then().statusCode(200);

        given().contentType("application/json")
                .body(Map.of("email", email, "password", "test1234"))
                .when().post(LOGIN)
                .then()
                .statusCode(200)
                .body("account.isAdmin", equalTo(true))
                .body("account.groups", hasItem("admin"))
                .body("account.groups", hasItem("user"))
                .body("token", notNullValue());
    }

    // ── A-C-03 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A-C-03: GET /auth/account returns isAdmin=true for admin token")
    void getAccountReturnsAdminTrueForAdminToken() {
        String token = registerVerifyAndLogin("admin-story7b@example.com");

        given().header("Authorization", "Bearer " + token)
                .when().get(ACCOUNT)
                .then()
                .statusCode(200)
                .body("isAdmin", equalTo(true))
                .body("groups", hasItem("admin"));
    }

    // ── A-C-06 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A-C-06: allowlist match is case-insensitive (Admin@Example.COM matches admin@example.com)")
    void allowlistMatchIsCaseInsensitive() {
        // allowlist has "Admin@Example.COM"; user registers with all-lowercase "admin@example.com"
        String email = "admin@example.com";
        register(email);
        String code = captureVerifyEmailCode();
        given().contentType("application/json")
                .body(Map.of("email", email, "code", code))
                .when().post(VERIFY_EMAIL)
                .then().statusCode(200);

        given().contentType("application/json")
                .body(Map.of("email", email, "password", "test1234"))
                .when().post(LOGIN)
                .then()
                .statusCode(200)
                .body("account.isAdmin", equalTo(true))
                .body("account.groups", hasItem("admin"));
    }
}
