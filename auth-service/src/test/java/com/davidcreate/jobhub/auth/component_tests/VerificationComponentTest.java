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
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Full email-verification and destructive-action flows. The {@link VerificationNotifier}
 * is mocked so the test can capture the token / code that would have been emailed.
 */
@QuarkusTest
@DisplayName("Verification Flow Component Tests")
class VerificationComponentTest {

    private static final String REGISTER = "/register";
    private static final String LOGIN = "/login";
    private static final String ACCOUNT = "/account";

    @InjectMock
    VerificationNotifier notifier;

    private String uniqueEmail;

    @BeforeEach
    void setUp() {
        Mockito.reset(notifier);
        uniqueEmail = "user-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    @DisplayName("registration emails a token; verify-email marks the account verified")
    void verifyEmailFlow() {
        register(uniqueEmail, "test1234");

        ArgumentCaptor<String> tokenCap = ArgumentCaptor.forClass(String.class);
        verify(notifier).sendEmailVerification(anyString(), tokenCap.capture());
        String token = tokenCap.getValue();

        given().contentType("application/json")
                .body(Map.of("token", token))
                .when().post(ACCOUNT + "/verify-email")
                .then().statusCode(204);

        String jwt = login(uniqueEmail, "test1234");
        given().header("Authorization", "Bearer " + jwt)
                .when().get(ACCOUNT)
                .then().statusCode(200)
                .body("emailVerified", equalTo(true));
    }

    @Test
    @DisplayName("verifications issues a code; delete-account consumes it and removes the user")
    void deleteAccountFlow() {
        register(uniqueEmail, "test1234");
        String jwt = login(uniqueEmail, "test1234");

        String verificationId = given()
                .header("Authorization", "Bearer " + jwt)
                .contentType("application/json")
                .body(Map.of("action", "delete-account"))
                .when().post(ACCOUNT + "/verifications")
                .then().statusCode(200)
                .body("verificationId", notNullValue())
                .body("expiresAt", notNullValue())
                .extract().jsonPath().getString("verificationId");

        ArgumentCaptor<String> codeCap = ArgumentCaptor.forClass(String.class);
        verify(notifier).sendActionCode(anyString(), eq(VerificationAction.DELETE_ACCOUNT), codeCap.capture());
        String code = codeCap.getValue();

        given().header("Authorization", "Bearer " + jwt)
                .contentType("application/json")
                .body(Map.of("verificationId", verificationId, "code", code))
                .when().delete(ACCOUNT)
                .then().statusCode(204);

        // user is gone — credentials no longer valid
        given().contentType("application/json")
                .body(Map.of("email", uniqueEmail, "password", "test1234"))
                .when().post(LOGIN)
                .then().statusCode(401);
    }

    @Test
    @DisplayName("delete-account rejects a wrong code → 400")
    void deleteAccountWrongCode() {
        register(uniqueEmail, "test1234");
        String jwt = login(uniqueEmail, "test1234");

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
    @DisplayName("verifications/consume validates a delete-all-applications code, then rejects reuse → 204 then 400")
    void consumeDeleteAllApplicationsFlow() {
        register(uniqueEmail, "test1234");
        String jwt = login(uniqueEmail, "test1234");

        String verificationId = given()
                .header("Authorization", "Bearer " + jwt)
                .contentType("application/json")
                .body(Map.of("action", "delete-all-applications"))
                .when().post(ACCOUNT + "/verifications")
                .then().statusCode(200)
                .extract().jsonPath().getString("verificationId");

        ArgumentCaptor<String> codeCap = ArgumentCaptor.forClass(String.class);
        verify(notifier).sendActionCode(anyString(), eq(VerificationAction.DELETE_ALL_APPLICATIONS), codeCap.capture());
        String code = codeCap.getValue();

        var body = Map.of("verificationId", verificationId, "code", code, "action", "delete-all-applications");

        given().header("Authorization", "Bearer " + jwt)
                .contentType("application/json").body(body)
                .when().post(ACCOUNT + "/verifications/consume")
                .then().statusCode(204);

        // single use — the second attempt is rejected
        given().header("Authorization", "Bearer " + jwt)
                .contentType("application/json").body(body)
                .when().post(ACCOUNT + "/verifications/consume")
                .then().statusCode(400);
    }

    @Test
    @DisplayName("verifications/consume rejects a code issued for a different action → 400")
    void consumeActionMismatch() {
        register(uniqueEmail, "test1234");
        String jwt = login(uniqueEmail, "test1234");

        String verificationId = given()
                .header("Authorization", "Bearer " + jwt)
                .contentType("application/json")
                .body(Map.of("action", "delete-account"))
                .when().post(ACCOUNT + "/verifications")
                .then().statusCode(200)
                .extract().jsonPath().getString("verificationId");

        ArgumentCaptor<String> codeCap = ArgumentCaptor.forClass(String.class);
        verify(notifier).sendActionCode(anyString(), eq(VerificationAction.DELETE_ACCOUNT), codeCap.capture());

        given().header("Authorization", "Bearer " + jwt)
                .contentType("application/json")
                .body(Map.of("verificationId", verificationId, "code", codeCap.getValue(),
                        "action", "delete-all-applications"))
                .when().post(ACCOUNT + "/verifications/consume")
                .then().statusCode(400);
    }

    private void register(String email, String password) {
        given().contentType("application/json")
                .body(Map.of("firstName", "Test", "lastName", "User", "email", email, "password", password))
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
