package com.davidcreate.jobhub.auth.component_tests;

import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Component tests for the auth-service REST surface, exercising the contract
 * defined in {@code api-contracts/openapi/auth-service.yaml}. The Quarkus app
 * sets {@code quarkus.http.root-path=/auth}, so the contract paths
 * {@code /register}, {@code /login}, {@code /account} become {@code /auth/...}.
 *
 * VerificationNotifier is mocked at class level so every test that calls
 * {@code register()} can capture the verification code without needing SMTP.
 * The {@code login()} helper verifies the email before logging in.
 *
 * Server-error (500) cases live in {@link AuthResourceFailureComponentTest}.
 */
@QuarkusTest
@DisplayName("Auth Resource Component Tests")
class AuthResourceComponentTest {

    private static final String REGISTER = "/register";
    private static final String LOGIN = "/login";
    private static final String ACCOUNT = "/account";
    private static final String VERIFY_EMAIL_PATH = ACCOUNT + "/verify-email";
    private static final String RESEND_PATH = ACCOUNT + "/resend-verification";

    @InjectMock
    VerificationNotifier notifier;

    private String uniqueEmail;

    @BeforeEach
    void setUp() {
        Mockito.reset(notifier);
        uniqueEmail = "user-" + UUID.randomUUID() + "@example.com";
    }

    @Nested
    @DisplayName("POST /auth/register")
    class Register {

        @Test
        @DisplayName("✓ valid request → 201 + RegisterResponse{account, verificationRequired:true}")
        void testRegisterSuccess() {
            given()
                    .contentType("application/json")
                    .body(Map.of(
                            "firstName", "Alice",
                            "lastName", "Martin",
                            "email", uniqueEmail,
                            "password", "test1234"))
                    .when().post(REGISTER)
                    .then()
                    .statusCode(201)
                    .body("account.email", equalTo(uniqueEmail))
                    .body("account.firstName", equalTo("Alice"))
                    .body("account.emailVerified", equalTo(false))
                    .body("account.id", notNullValue())
                    .body("verificationRequired", equalTo(true));
        }

        @Test
        @DisplayName("✗ duplicate email → 409")
        void testRegisterDuplicate() {
            Map<String, Object> body = Map.of(
                    "firstName", "Alice", "lastName", "M",
                    "email", uniqueEmail, "password", "test1234");

            given().contentType("application/json").body(body)
                    .when().post(REGISTER).then().statusCode(201);

            given().contentType("application/json").body(body)
                    .when().post(REGISTER).then().statusCode(409);
        }

        @Test
        @DisplayName("✗ invalid email → 400")
        void testRegisterInvalidEmail() {
            given()
                    .contentType("application/json")
                    .body(Map.of(
                            "firstName", "Alice", "lastName", "M",
                            "email", "not-an-email", "password", "test1234"))
                    .when().post(REGISTER)
                    .then().statusCode(400);
        }
    }

    @Nested
    @DisplayName("POST /auth/login")
    class Login {

        @Test
        @DisplayName("✓ valid credentials (verified) → 200 + LoginResponse (token + account)")
        void testLoginSuccess() {
            registerAndVerify(uniqueEmail, "test1234");

            given()
                    .contentType("application/json")
                    .body(Map.of("email", uniqueEmail, "password", "test1234"))
                    .when().post(LOGIN)
                    .then()
                    .statusCode(200)
                    .body("token", notNullValue())
                    .body("expiresIn", notNullValue())
                    .body("account.email", equalTo(uniqueEmail));
        }

        @Test
        @DisplayName("✗ wrong password → 401")
        void testLoginWrongPassword() {
            registerAndVerify(uniqueEmail, "test1234");

            given()
                    .contentType("application/json")
                    .body(Map.of("email", uniqueEmail, "password", "wrongpass"))
                    .when().post(LOGIN)
                    .then().statusCode(401);
        }

        @Test
        @DisplayName("✗ unknown email → 401")
        void testLoginUnknownEmail() {
            given()
                    .contentType("application/json")
                    .body(Map.of("email", "nobody-" + UUID.randomUUID() + "@example.com",
                            "password", "test1234"))
                    .when().post(LOGIN)
                    .then().statusCode(401);
        }
    }

    @Nested
    @DisplayName("GET /auth/account")
    class GetAccount {

        @Test
        @DisplayName("✗ no token → 401")
        void testGetAccountUnauthorized() {
            given().when().get(ACCOUNT).then().statusCode(401);
        }

        @Test
        @DisplayName("✓ with token → 200 + caller profile")
        void testGetAccountWithToken() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            given()
                    .header("Authorization", "Bearer " + token)
                    .when().get(ACCOUNT)
                    .then()
                    .statusCode(200)
                    .body("email", equalTo(uniqueEmail));
        }
    }

    @Nested
    @DisplayName("PATCH /auth/account")
    class UpdateAccount {

        @Test
        @DisplayName("✓ updates firstName, visible on subsequent GET")
        void testPatchAccount() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            given()
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(Map.of("firstName", "Alicia"))
                    .when().patch(ACCOUNT)
                    .then()
                    .statusCode(200)
                    .body("firstName", equalTo("Alicia"));

            given()
                    .header("Authorization", "Bearer " + token)
                    .when().get(ACCOUNT)
                    .then()
                    .statusCode(200)
                    .body("firstName", equalTo("Alicia"));
        }

        @Test
        @DisplayName("✗ no token → 401")
        void testPatchUnauthorized() {
            given()
                    .contentType("application/json")
                    .body(Map.of("firstName", "Alicia"))
                    .when().patch(ACCOUNT)
                    .then().statusCode(401);
        }
    }

    @Nested
    @DisplayName("POST /auth/account/change-password")
    class ChangePassword {

        @Test
        @DisplayName("✓ correct current password → 204; next login works only with new password")
        void testChangePasswordRotates() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            given()
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(Map.of("currentPassword", "test1234", "newPassword", "newpass1"))
                    .when().post(ACCOUNT + "/change-password")
                    .then().statusCode(204);

            given().contentType("application/json")
                    .body(Map.of("email", uniqueEmail, "password", "test1234"))
                    .when().post(LOGIN)
                    .then().statusCode(401);

            given().contentType("application/json")
                    .body(Map.of("email", uniqueEmail, "password", "newpass1"))
                    .when().post(LOGIN)
                    .then().statusCode(200);
        }

        @Test
        @DisplayName("✗ wrong current password → 401")
        void testChangePasswordWrongCurrent() {
            registerAndVerify(uniqueEmail, "test1234");
            String token = login(uniqueEmail, "test1234");

            given()
                    .header("Authorization", "Bearer " + token)
                    .contentType("application/json")
                    .body(Map.of("currentPassword", "WRONG", "newPassword", "newpass1"))
                    .when().post(ACCOUNT + "/change-password")
                    .then().statusCode(401);
        }

        @Test
        @DisplayName("✗ no token → 401")
        void testChangePasswordUnauthorized() {
            given()
                    .contentType("application/json")
                    .body(Map.of("currentPassword", "test1234", "newPassword", "newpass1"))
                    .when().post(ACCOUNT + "/change-password")
                    .then().statusCode(401);
        }
    }

    @Nested
    @DisplayName("Verification — validation & auth")
    class Verification {

        @Test
        @DisplayName("POST /auth/account/verify-email ✗ wrong code for unknown email → 400")
        void verifyEmailBadRequest() {
            given()
                    .contentType("application/json")
                    .body(Map.of("email", "nobody-" + UUID.randomUUID() + "@example.com", "code", "000000"))
                    .when().post(VERIFY_EMAIL_PATH)
                    .then().statusCode(400);
        }

        @Test
        @DisplayName("POST /auth/account/resend-verification ✓ unknown email → 204 (silent, no enumeration)")
        void resendUnknownEmail() {
            given()
                    .contentType("application/json")
                    .body(Map.of("email", "nobody-" + UUID.randomUUID() + "@example.com"))
                    .when().post(RESEND_PATH)
                    .then().statusCode(204);
        }

        @Test
        @DisplayName("POST /auth/account/resend-verification ✗ malformed email → 400")
        void resendInvalidEmail() {
            given()
                    .contentType("application/json")
                    .body(Map.of("email", "not-an-email"))
                    .when().post(RESEND_PATH)
                    .then().statusCode(400);
        }

        @Test
        @DisplayName("DELETE /auth/account ✗ no token → 401")
        void deleteAccountUnauthorized() {
            given()
                    .contentType("application/json")
                    .body(Map.of("verificationId", UUID.randomUUID().toString(), "code", "123456"))
                    .when().delete(ACCOUNT)
                    .then().statusCode(401);
        }

        @Test
        @DisplayName("POST /auth/account/verifications ✗ no token → 401")
        void requestVerificationUnauthorized() {
            given()
                    .contentType("application/json")
                    .body(Map.of("action", "delete-account"))
                    .when().post(ACCOUNT + "/verifications")
                    .then().statusCode(401);
        }
    }

    // --- helpers ---

    /** Register only (201 expected). Notifier mock must be set up by the caller. */
    private void registerOnly(String email, String password) {
        given().contentType("application/json")
                .body(Map.of("firstName", "Test", "lastName", "User",
                        "email", email, "password", password))
                .when().post(REGISTER)
                .then().statusCode(201);
    }

    /**
     * Register + capture the VERIFY_EMAIL code + verify the account.
     * After this call the account is email-verified and ready for login.
     */
    private void registerAndVerify(String email, String password) {
        registerOnly(email, password);

        ArgumentCaptor<String> codeCap = ArgumentCaptor.forClass(String.class);
        Mockito.verify(notifier).sendActionCode(anyString(), eq(VerificationAction.VERIFY_EMAIL), codeCap.capture());
        String code = codeCap.getValue();

        given().contentType("application/json")
                .body(Map.of("email", email, "code", code))
                .when().post(VERIFY_EMAIL_PATH)
                .then().statusCode(200);

        Mockito.reset(notifier);
    }

    private String login(String email, String password) {
        return given().contentType("application/json")
                .body(Map.of("email", email, "password", password))
                .when().post(LOGIN)
                .then().statusCode(200)
                .extract().jsonPath().getString("token");
    }
}
