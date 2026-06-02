package com.davidcreate.jobhub.auth.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Component tests for the auth-service REST surface, exercising the contract
 * defined in {@code api-contracts/openapi/auth-service.yaml}. The Quarkus app
 * sets {@code quarkus.http.root-path=/auth}, so the contract paths
 * {@code /register}, {@code /login}, {@code /account} become {@code /auth/...}.
 *
 * <p>Server-error (500) cases live in {@link AuthResourceFailureComponentTest}.
 */
@QuarkusTest
@DisplayName("Auth Resource Component Tests")
class AuthResourceComponentTest {

    private static final String REGISTER = "/register";
    private static final String LOGIN = "/login";
    private static final String ACCOUNT = "/account";

    private String uniqueEmail;

    @BeforeEach
    void setUp() {
        uniqueEmail = "user-" + UUID.randomUUID() + "@example.com";
    }

    @Nested
    @DisplayName("POST /auth/register")
    class Register {

        @Test
        @DisplayName("✓ valid request → 201 + AccountResponse body")
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
                    .body("email", equalTo(uniqueEmail))
                    .body("firstName", equalTo("Alice"))
                    .body("emailVerified", equalTo(false))
                    .body("id", notNullValue());
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
        @DisplayName("✓ valid credentials → 200 + LoginResponse (token + account)")
        void testLoginSuccess() {
            register(uniqueEmail, "test1234");

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
            register(uniqueEmail, "test1234");

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
            register(uniqueEmail, "test1234");
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
            register(uniqueEmail, "test1234");
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
            register(uniqueEmail, "test1234");
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
            register(uniqueEmail, "test1234");
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
        @DisplayName("POST /auth/account/verify-email ✗ bad token → 400")
        void verifyEmailBadToken() {
            given()
                    .contentType("application/json")
                    .body(Map.of("token", "bogus-" + UUID.randomUUID()))
                    .when().post(ACCOUNT + "/verify-email")
                    .then().statusCode(400);
        }

        @Test
        @DisplayName("POST /auth/account/resend-verification ✓ unknown email → 204 (silent, no enumeration)")
        void resendUnknownEmail() {
            given()
                    .contentType("application/json")
                    .body(Map.of("email", "nobody-" + UUID.randomUUID() + "@example.com"))
                    .when().post(ACCOUNT + "/resend-verification")
                    .then().statusCode(204);
        }

        @Test
        @DisplayName("POST /auth/account/resend-verification ✗ malformed email → 400")
        void resendInvalidEmail() {
            given()
                    .contentType("application/json")
                    .body(Map.of("email", "not-an-email"))
                    .when().post(ACCOUNT + "/resend-verification")
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

    private void register(String email, String password) {
        given().contentType("application/json")
                .body(Map.of(
                        "firstName", "Test", "lastName", "User",
                        "email", email, "password", password))
                .when().post(REGISTER)
                .then().statusCode(201);
    }

    private String login(String email, String password) {
        return given().contentType("application/json")
                .body(Map.of("email", email, "password", password))
                .when().post(LOGIN)
                .then().statusCode(200)
                .extract().jsonPath().getString("token");
    }
}
