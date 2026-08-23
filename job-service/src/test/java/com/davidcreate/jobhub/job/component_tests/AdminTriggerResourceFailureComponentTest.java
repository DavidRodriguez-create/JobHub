package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.TwoFactorStubs;
import com.davidcreate.jobhub.job.component_tests.support.WireMockAuthServerResource;
import com.davidcreate.jobhub.job.component_tests.support.WireMockCrawlerServerResource;
import com.davidcreate.jobhub.job.domain.port.out.TriggerRequestRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 5xx-path component tests for the admin trigger endpoints (Story #7 / ADR 0003;
 * 2FA gate: ADR 0019; crawler-service call: ADR 0033, ticket #583).
 *
 * <p>Lives in its own class because {@code @InjectMock} replaces the bean for the
 * whole {@code @QuarkusTest} class — see {@code CLAUDE.md} component test rules.
 * Since ADR 0033 the queue/cancel error paths (TR-23/24/30) exercise the *real*
 * {@code CrawlerTriggerGateway} adapter against {@link WireMockCrawlerServerResource}
 * rather than mocking the gateway away, so a bad/unreachable crawler-service response
 * maps through the actual adapter code. Only the status-read failure
 * ({@code statusServerError}) still needs {@code @InjectMock} on the (still real)
 * {@link TriggerRequestRepository}.
 *
 * <p>Every admin here stubs as not having 2FA enabled by default (QAE note 0.1).
 */
@QuarkusTest
@QuarkusTestResource(WireMockAuthServerResource.class)
@QuarkusTestResource(WireMockCrawlerServerResource.class)
@DisplayName("Admin Trigger Resource Failure Component Tests")
class AdminTriggerResourceFailureComponentTest {

    private static final String TRIGGERS = "/jobs/admin/triggers";
    private static final String STATUS = "/jobs/admin/triggers/status";
    private static final String ADMIN_SUB = "20000000-0000-0000-0000-000000000080";

    @InjectMock
    TriggerRequestRepository triggerRequestRepository;

    private WireMockServer authWireMock() {
        return WireMockAuthServerResource.server();
    }

    private WireMockServer crawlerWireMock() {
        return WireMockCrawlerServerResource.server();
    }

    @BeforeEach
    void stubNoTwoFactor() {
        TwoFactorStubs.stubNoTwoFactorForEveryAdmin(authWireMock());
    }

    @BeforeEach
    void resetCrawlerStubs() {
        crawlerWireMock().resetAll();
    }

    @Test
    @TestSecurity(user = ADMIN_SUB, roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = ADMIN_SUB))
    @DisplayName("✗ POST /jobs/admin/triggers: crawler-service returns an unexpected 500 → 500 {error, message}")
    void postServerError() {
        crawlerWireMock().stubFor(post(urlEqualTo("/internal/trigger-requests"))
                .willReturn(aResponse().withStatus(500)));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(500)
                .body("error", org.hamcrest.Matchers.equalTo("Internal Server Error"))
                .body("message", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000086", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000086"))
    @DisplayName("C23: POST /jobs/admin/triggers: auth-service 2FA-verify throttled (429) passes through, crawler-service never called")
    void postTwoFactorThrottledPassesThrough() {
        authWireMock().resetAll();
        authWireMock().stubFor(post(urlEqualTo("/auth/internal/two-factor/verify"))
                .willReturn(aResponse().withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Too Many Requests\",\"message\":\"slow down\"}")));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl", "code", "123456"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(429);

        crawlerWireMock().verify(0, com.github.tomakehurst.wiremock.client.WireMock
                .postRequestedFor(urlEqualTo("/internal/trigger-requests")));
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000081", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000081"))
    @DisplayName("✗ GET /jobs/admin/triggers/status — DB error on read → 500 {error, message}")
    void statusServerError() {
        when(triggerRequestRepository.findMostRecent(any()))
                .thenThrow(new RuntimeException("Simulated DB connection crash"));

        given().when().get(STATUS)
                .then()
                .statusCode(500)
                .body("error", org.hamcrest.Matchers.equalTo("Internal Server Error"))
                .body("message", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000082", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000082"))
    @DisplayName("JS-COMP-FAIL-01: ✗ POST /jobs/admin/triggers/crawl/cancel: crawler-service returns an unexpected 500 → 500 {error, message}")
    void cancelServerError() {
        crawlerWireMock().stubFor(post(urlEqualTo("/internal/trigger-requests/crawl/cancel"))
                .willReturn(aResponse().withStatus(500)));

        given().when().post("/jobs/admin/triggers/crawl/cancel")
                .then()
                .statusCode(500)
                .body("error", org.hamcrest.Matchers.equalTo("Internal Server Error"))
                .body("message", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000083", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000083"))
    @DisplayName("TC-384-J33/EC-384-6: GET /jobs/admin/triggers/status — auth-service 2FA-status call fails → 500 {error, message}")
    void statusFailsWhenTwoFactorStatusCallFails() {
        authWireMock().resetAll();
        authWireMock().stubFor(get(urlPathMatching("/auth/internal/users/.*/two-factor"))
                .willReturn(aResponse().withStatus(500)));

        given().when().get(STATUS)
                .then()
                .statusCode(500)
                .body("error", org.hamcrest.Matchers.equalTo("Internal Server Error"))
                .body("message", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000084", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000084"))
    @DisplayName("TC-384-J34/EC-384-6: POST /jobs/admin/triggers — auth-service 2FA-verify call fails → 500 {error, message}")
    void postFailsWhenTwoFactorVerifyCallFails() {
        authWireMock().resetAll();
        authWireMock().stubFor(post(urlEqualTo("/auth/internal/two-factor/verify"))
                .willReturn(aResponse().withStatus(500)));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl", "code", "123456"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(500)
                .body("error", org.hamcrest.Matchers.equalTo("Internal Server Error"))
                .body("message", org.hamcrest.Matchers.notNullValue());
    }

    // ── TR-23/TR-24: crawler-service unreachable → 503, no retry, honest failure ──
    // Simulated via WireMock's CONNECTION_RESET_BY_PEER fault: the REST client sees a
    // transport-level failure (ProcessingException), same as a real connection refused.

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000120", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000120"))
    @DisplayName("TR-23: crawler-service unreachable on queue → 503 'Crawler Unavailable', nothing started")
    void postCrawlerUnavailableIsServiceUnavailable() {
        crawlerWireMock().stubFor(post(urlEqualTo("/internal/trigger-requests"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(503)
                .body("error", org.hamcrest.Matchers.equalTo("Crawler Unavailable"))
                .body("message", org.hamcrest.Matchers.containsString("Nothing was started"));
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000121", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000121"))
    @DisplayName("TR-24: crawler-service unreachable on cancel → 503 'Crawler Unavailable', nothing changed")
    void cancelCrawlerUnavailableIsServiceUnavailable() {
        crawlerWireMock().stubFor(post(urlEqualTo("/internal/trigger-requests/crawl/cancel"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        given()
                .when().post("/jobs/admin/triggers/crawl/cancel")
                .then()
                .statusCode(503)
                .body("error", org.hamcrest.Matchers.equalTo("Crawler Unavailable"))
                .body("message", org.hamcrest.Matchers.containsString("Nothing was changed"));
    }

    // ── TR-30: wrong service key configured is a config defect, not a normal path ──

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000122", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000122"))
    @DisplayName("TR-30: crawler-service rejects the service key (401) → 500 from job-service, not retried, not surfaced as 503")
    void postWrongServiceKeyIsInternalServerErrorNotServiceUnavailable() {
        crawlerWireMock().stubFor(post(urlEqualTo("/internal/trigger-requests"))
                .willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Unauthorized\",\"message\":\"missing or invalid X-Service-Key\"}")));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(500)
                .body("error", org.hamcrest.Matchers.equalTo("Internal Server Error"))
                .body("message", org.hamcrest.Matchers.notNullValue());

        crawlerWireMock().verify(1, com.github.tomakehurst.wiremock.client.WireMock
                .postRequestedFor(urlEqualTo("/internal/trigger-requests")));
    }
}
