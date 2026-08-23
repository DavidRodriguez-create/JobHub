package com.davidcreate.jobhub.auth.component_tests;

import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.application.port.out.VerificationCodeRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * EV-CF-01: verify repo crash → 500
 * EV-CF-02: resend repo crash → 500
 * EV-CF-03: register repo crash → 500
 *
 * Separate @QuarkusTest class per CLAUDE.md — mixing mocked and real beans
 * in the same class loses the real DevServices DB.
 */
@QuarkusTest
@DisplayName("Auth Resource Failure Component Tests — EV-CF-01..03")
class AuthResourceFailureComponentTest {

    private static final String REGISTER = "/register";
    private static final String LOGIN = "/login";
    private static final String ACCOUNT = "/account";
    private static final String VERIFY_EMAIL_PATH = ACCOUNT + "/verify-email";
    private static final String RESEND_PATH = ACCOUNT + "/resend-verification";

    @InjectMock
    UserRepository userRepository;

    @InjectMock
    VerificationCodeRepository verificationCodeRepository;

    // EV-CF-03
    @Test
    @DisplayName("EV-CF-03: POST /auth/register ✗ repository crash → 500 via GlobalExceptionMapper")
    void testRegisterServerError() {
        when(userRepository.findByEmail(anyString()))
                .thenThrow(new RuntimeException("simulated DB crash"));

        given()
                .contentType("application/json")
                .body(Map.of(
                        "firstName", "Alice", "lastName", "M",
                        "email", "fail@example.com", "password", "test1234"))
                .when().post(REGISTER)
                .then().statusCode(500);
    }

    // EV-CF-01
    @Test
    @DisplayName("EV-CF-01: POST /auth/account/verify-email ✗ repository crash → 500")
    void testVerifyEmailServerError() {
        when(userRepository.findByEmail(anyString()))
                .thenThrow(new RuntimeException("simulated DB crash"));

        given()
                .contentType("application/json")
                .body(Map.of("email", "fail@example.com", "code", "123456"))
                .when().post(VERIFY_EMAIL_PATH)
                .then().statusCode(500);
    }

    // EV-CF-02
    @Test
    @DisplayName("EV-CF-02: POST /auth/account/resend-verification ✗ repository crash → 500")
    void testResendServerError() {
        when(userRepository.findByEmail(anyString()))
                .thenThrow(new RuntimeException("simulated DB crash"));

        given()
                .contentType("application/json")
                .body(Map.of("email", "fail@example.com"))
                .when().post(RESEND_PATH)
                .then().statusCode(500);
    }

    @Test
    @DisplayName("POST /auth/login ✗ repository crash → 500 via GlobalExceptionMapper")
    void testLoginServerError() {
        when(userRepository.findByEmail(anyString()))
                .thenThrow(new RuntimeException("simulated DB crash"));

        given()
                .contentType("application/json")
                .body(Map.of("email", "fail@example.com", "password", "test1234"))
                .when().post(LOGIN)
                .then().statusCode(500);
    }
}
