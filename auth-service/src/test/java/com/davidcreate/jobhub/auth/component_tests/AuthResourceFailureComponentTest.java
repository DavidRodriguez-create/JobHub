package com.davidcreate.jobhub.auth.component_tests;

import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@DisplayName("Auth Resource Failure Component Tests")
class AuthResourceFailureComponentTest {

    private static final String REGISTER = "/register";
    private static final String LOGIN = "/login";

    @InjectMock
    UserRepository userRepository;

    @Test
    @DisplayName("POST /auth/register ✗ repository crash → 500 via GlobalExceptionMapper")
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
