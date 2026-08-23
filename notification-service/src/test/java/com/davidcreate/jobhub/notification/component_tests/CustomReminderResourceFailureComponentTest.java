package com.davidcreate.jobhub.notification.component_tests;

import com.davidcreate.jobhub.notification.component_tests.support.WireMockAppInternalResource;
import com.davidcreate.jobhub.notification.domain.port.out.CustomReminderRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(WireMockAppInternalResource.class)
@DisplayName("CustomReminderResource Failure Component Tests")
class CustomReminderResourceFailureComponentTest {

    private static final String BASE = "/notifications/custom-reminders";
    private static final String USER_A = "ee000000-0000-0000-0000-000000000001";

    @InjectMock
    CustomReminderRepository repository;

    @BeforeEach
    void resetWireMock() {
        WireMockAppInternalResource.server().resetAll();
    }

    private void stubOwnership204() {
        WireMockAppInternalResource.server().stubFor(head(urlPathEqualTo("/internal/applications/" +
                "ea000000-0000-0000-0000-000000000001/owner/" + USER_A))
                .willReturn(aResponse().withStatus(204)));
    }

    // CR-C-060
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-060: POST 500 when repository throws unexpectedly")
    void postRepositoryThrows() {
        stubOwnership204();
        when(repository.save(any())).thenThrow(new RuntimeException("db down"));

        given().contentType("application/json")
                .body(Map.of(
                        "applicationId", "ea000000-0000-0000-0000-000000000001",
                        "title", "Prep call",
                        "triggerAtUtc", Instant.now().plusSeconds(3600).toString(),
                        "channels", List.of("IN_APP")))
                .when().post(BASE)
                .then().statusCode(500)
                .body("error", org.hamcrest.Matchers.notNullValue())
                .body("message", org.hamcrest.Matchers.notNullValue());
    }

    // CR-C-061
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-061: GET single 500 when repository throws")
    void getRepositoryThrows() {
        when(repository.findByIdForUser(any(), any())).thenThrow(new RuntimeException("db down"));

        given().when().get(BASE + "/" + UUID.randomUUID())
                .then().statusCode(500);
    }

    // CR-C-062 / NS-CF-02
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-062/NS-CF-02: PUT 500 when repository throws on update")
    void putRepositoryThrows() {
        when(repository.findByIdForUser(any(), any())).thenThrow(new RuntimeException("db down"));

        given().contentType("application/json")
                .body(Map.of("note", "New note"))
                .when().put(BASE + "/" + UUID.randomUUID())
                .then().statusCode(500)
                .body("error", org.hamcrest.Matchers.notNullValue())
                .body("message", org.hamcrest.Matchers.notNullValue());
    }

    // CR-C-063
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-063: DELETE 500 when repository throws on mark-cancelled")
    void deleteRepositoryThrows() {
        when(repository.findByIdForUser(any(), any())).thenThrow(new RuntimeException("db down"));

        given().when().delete(BASE + "/" + UUID.randomUUID())
                .then().statusCode(500);
    }

    // CR-C-064
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-C-064: GET list-mine 500 when repository throws")
    void listMineRepositoryThrows() {
        when(repository.findAllForUser(any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new RuntimeException("db down"));

        given().when().get(BASE)
                .then().statusCode(500);
    }

    // CR-NS-F57 (new)
    @Test
    @TestSecurity(user = USER_A, roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = USER_A))
    @DisplayName("CR-NS-F57: GET filtered list 500 when repository throws on the applicationId-scoped query")
    void listFilteredByApplicationRepositoryThrows() {
        when(repository.findAllForUserAndApplication(any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new RuntimeException("db down"));

        given().when().get(BASE + "?applicationId=" + UUID.randomUUID())
                .then().statusCode(500)
                .body("error", org.hamcrest.Matchers.notNullValue())
                .body("message", org.hamcrest.Matchers.notNullValue());
    }
}
