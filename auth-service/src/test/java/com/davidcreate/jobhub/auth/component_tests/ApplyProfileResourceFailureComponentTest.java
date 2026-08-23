package com.davidcreate.jobhub.auth.component_tests;

import com.davidcreate.jobhub.auth.application.port.out.ApplyProfileRepository;
import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * BE-C14: repository failure -> 500, via GlobalExceptionMapper. Separate top-level
 * @QuarkusTest class per CLAUDE.md — mixing @InjectMock with real DevServices beans
 * in the same class loses the real DB (see ApplyProfileResourceComponentTest).
 */
@QuarkusTest
@DisplayName("Apply Profile Resource Failure Component Tests — BE-C14")
class ApplyProfileResourceFailureComponentTest {

    private static final String BASE = "/account/apply-profile";
    private static final String REGISTER = "/register";
    private static final String LOGIN = "/login";
    private static final String VERIFY_EMAIL_PATH = "/account/verify-email";

    @InjectMock
    ApplyProfileRepository applyProfileRepository;

    @InjectMock
    VerificationNotifier notifier;

    @Test
    @DisplayName("BE-C14: GET -> 500 when the repository throws on find")
    void getServerErrorWhenRepositoryThrowsOnFind() {
        String email = "apply-profile-fail-get-" + UUID.randomUUID() + "@example.com";
        String token = registerVerifyAndLogin(email, "test1234");

        when(applyProfileRepository.findByUserId(any())).thenThrow(new RuntimeException("simulated DB crash"));

        given().header("Authorization", "Bearer " + token)
                .when().get(BASE)
                .then().statusCode(500);
    }

    @Test
    @DisplayName("BE-C14: PUT -> 500 when the repository throws on save")
    void putServerErrorWhenRepositoryThrowsOnSave() {
        String email = "apply-profile-fail-put-" + UUID.randomUUID() + "@example.com";
        String token = registerVerifyAndLogin(email, "test1234");

        when(applyProfileRepository.findByUserId(any())).thenReturn(java.util.Optional.empty());
        when(applyProfileRepository.save(any())).thenThrow(new RuntimeException("simulated DB crash"));

        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("workAuthorization", "US Citizen"))
                .when().put(BASE)
                .then().statusCode(500);
    }

    private String registerVerifyAndLogin(String email, String password) {
        given().contentType("application/json")
                .body(Map.of("firstName", "Test", "lastName", "User",
                        "email", email, "password", password))
                .when().post(REGISTER)
                .then().statusCode(201);

        ArgumentCaptor<String> codeCap = ArgumentCaptor.forClass(String.class);
        Mockito.verify(notifier).sendActionCode(anyString(), eq(VerificationAction.VERIFY_EMAIL), codeCap.capture());
        String code = codeCap.getValue();

        given().contentType("application/json")
                .body(Map.of("email", email, "code", code))
                .when().post(VERIFY_EMAIL_PATH)
                .then().statusCode(200);

        Mockito.reset(notifier);

        return given().contentType("application/json")
                .body(Map.of("email", email, "password", password))
                .when().post(LOGIN)
                .then().statusCode(200)
                .extract().jsonPath().getString("token");
    }
}
