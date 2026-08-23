package com.davidcreate.jobhub.notification.component_tests;

import com.davidcreate.jobhub.notification.domain.port.out.NotificationRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Failure-path component tests for {@code NotificationResource}'s notification-center
 * endpoints.
 *
 * <p>Lives in its own class because {@code @InjectMock} replaces the bean for the whole
 * {@code @QuarkusTest} class; we don't want to lose the real DevServices-backed repository
 * in {@link NotificationsResourceComponentTest}.
 */
@QuarkusTest
@DisplayName("Notifications Resource Failure Component Tests")
class NotificationsResourceFailureComponentTest {

    private static final String BASE = "/notifications";
    private static final String SOME_NOTIFICATION_ID = "f0000000-0000-0000-0000-000000000001";

    @InjectMock
    NotificationRepository repository;

    // TC-B-F-01
    @Test
    @TestSecurity(user = "d0000000-0000-0000-0000-000000000003", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "d0000000-0000-0000-0000-000000000003"))
    @DisplayName("TC-B-F-01: GET /notifications returns 500 when the repository throws")
    void listServerError() {
        when(repository.findByUserId(any(), anyInt(), anyInt(), any()))
                .thenThrow(new RuntimeException("Simulated DB connection crash"));

        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(500)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // TC-B-F-02
    @Test
    @TestSecurity(user = "d0000000-0000-0000-0000-000000000004", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "d0000000-0000-0000-0000-000000000004"))
    @DisplayName("TC-B-F-02: GET /notifications/unread-count returns 500 when the repository throws")
    void unreadCountServerError() {
        when(repository.countByUserId(any(), any()))
                .thenThrow(new RuntimeException("Simulated DB connection crash"));

        given().when().get(BASE + "/unread-count")
                .then().statusCode(500)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // TC-B-F-03
    @Test
    @TestSecurity(user = "d0000000-0000-0000-0000-000000000005", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "d0000000-0000-0000-0000-000000000005"))
    @DisplayName("TC-B-F-03: PATCH /notifications/{id}/read returns 500 when the repository throws")
    void markReadServerError() {
        when(repository.findByIdAndUserId(any(), any()))
                .thenThrow(new RuntimeException("Simulated DB connection crash"));

        given().when().patch(BASE + "/" + SOME_NOTIFICATION_ID + "/read")
                .then().statusCode(500)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // TC-B-F-04
    @Test
    @TestSecurity(user = "d0000000-0000-0000-0000-000000000006", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "d0000000-0000-0000-0000-000000000006"))
    @DisplayName("TC-B-F-04: PATCH /notifications/read-all returns 500 when the repository throws")
    void markAllReadServerError() {
        doThrow(new RuntimeException("Simulated DB write crash"))
                .when(repository).markAllRead(any());

        given().when().patch(BASE + "/read-all")
                .then().statusCode(500)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // TC-206-B-09 (TC-B-F-05)
    @Test
    @TestSecurity(user = "d0000000-0000-0000-0000-000000000007", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "d0000000-0000-0000-0000-000000000007"))
    @DisplayName("TC-206-B-09: DELETE /notifications/{id} returns 500 when the repository throws")
    void deleteServerError() {
        when(repository.deleteByIdAndUser(any(), any()))
                .thenThrow(new RuntimeException("Simulated DB write crash"));

        given().when().delete(BASE + "/" + SOME_NOTIFICATION_ID)
                .then().statusCode(500)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // NS244-CF-01
    @Test
    @TestSecurity(user = "d0000000-0000-0000-0000-000000000008", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "d0000000-0000-0000-0000-000000000008"))
    @DisplayName("NS244-CF-01: GET /notifications returns 500 when the repository throws (regression: companyLogoUrl on model does not introduce new failure mode)")
    void listServerErrorWithCompanyLogoUrlFieldOnModelIsStillServerError() {
        when(repository.findByUserId(any(), anyInt(), anyInt(), any()))
                .thenThrow(new RuntimeException("Simulated DB connection crash including companyLogoUrl field"));

        given().queryParam("page", 0).queryParam("size", 20).queryParam("readStatus", "all")
                .when().get(BASE)
                .then().statusCode(500)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }
}
