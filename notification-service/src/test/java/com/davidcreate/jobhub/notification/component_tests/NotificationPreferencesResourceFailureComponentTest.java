package com.davidcreate.jobhub.notification.component_tests;

import com.davidcreate.jobhub.notification.domain.port.out.NotificationPreferencesRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Failure-path component tests for {@code NotificationPreferencesResource}.
 *
 * <p>Lives in its own class because {@code @InjectMock} replaces the bean for the whole
 * {@code @QuarkusTest} class; we don't want to lose the real DevServices-backed repository
 * in {@link NotificationPreferencesResourceComponentTest}.
 */
@QuarkusTest
@DisplayName("Notification Preferences Resource Failure Component Tests")
class NotificationPreferencesResourceFailureComponentTest {

    private static final String BASE = "/notifications/preferences";

    @InjectMock
    NotificationPreferencesRepository repository;

    // TC-17
    @Test
    @TestSecurity(user = "d0000000-0000-0000-0000-000000000001", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "d0000000-0000-0000-0000-000000000001"))
    @DisplayName("TC-17: GET returns 500 with an ErrorResponse body when the repository throws")
    void getServerError() {
        when(repository.findByUserId(any()))
                .thenThrow(new RuntimeException("Simulated DB connection crash"));

        given().when().get(BASE)
                .then().statusCode(500)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // TC-18
    @Test
    @TestSecurity(user = "d0000000-0000-0000-0000-000000000002", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "d0000000-0000-0000-0000-000000000002"))
    @DisplayName("TC-18: PUT returns 500 with an ErrorResponse body when the repository throws")
    void putServerError() {
        when(repository.findByUserId(any())).thenReturn(Optional.empty());
        when(repository.upsert(any()))
                .thenThrow(new RuntimeException("Simulated DB write crash"));

        given().contentType("application/json")
                .body(Map.of("ghostedAlert", false))
                .when().put(BASE)
                .then().statusCode(500)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }
}
