package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.TwoFactorGateProfile;
import com.davidcreate.jobhub.job.component_tests.support.WireMockAuthServerResource;
import com.davidcreate.jobhub.job.component_tests.support.WireMockCrawlerServerResource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Component tests for {@code POST /jobs/admin/triggers}' 2FA gate (ADR 0019),
 * stubbing auth-service's {@code POST /auth/internal/two-factor/verify} via
 * WireMock. TC-384-J14..J21/J25 (section D.1 of the QAE test-case doc); TC-384-J22
 * (gate order vs. the disabled toggle) lives in
 * {@code AdminTriggerDisabledComponentTest} instead, since it needs
 * {@code jobhub.admin.trigger.enabled=false}.
 */
@QuarkusTest
@TestProfile(TwoFactorGateProfile.class)
@DisplayName("Admin Trigger Resource Component Tests — 2FA gate on POST")
class AdminTriggerTwoFactorGateComponentTest {

    private static final String TRIGGERS = "/jobs/admin/triggers";
    private static final String VERIFY_PATH = "/auth/internal/two-factor/verify";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Inject
    EntityManager entityManager;

    private WireMockServer wireMock() {
        return WireMockAuthServerResource.server();
    }

    private WireMockServer crawlerWireMock() {
        return WireMockCrawlerServerResource.server();
    }

    @BeforeEach
    void clearTriggerRequestsAndStubs() {
        QuarkusTransaction.requiringNew()
                .run(() -> entityManager.createQuery("delete from TriggerRequestEntity").executeUpdate());
        wireMock().resetAll();
        crawlerWireMock().resetAll();
        stubQueueAccepted();
    }

    private void stubVerify(int status, String body) {
        wireMock().stubFor(post(urlEqualTo(VERIFY_PATH))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    // ADR 0033: since job-service no longer pre-checks dedupe locally, every 202 case
    // needs crawler-service to accept the queue call. Dedupe tests re-stub 409 between
    // the two POSTs: WireMock resolves equal-priority stubs most-recently-added-first.
    private void stubQueueAccepted() {
        crawlerWireMock().stubFor(post(urlEqualTo("/internal/trigger-requests"))
                .willReturn(aResponse().withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"" + java.util.UUID.randomUUID() + "\",\"kind\":\"crawl\","
                                + "\"status\":\"queued\",\"origin\":\"manual\","
                                + "\"requestedAt\":\"" + java.time.OffsetDateTime.now() + "\","
                                + "\"finishedAt\":null,\"resultSummary\":null}")));
    }

    private void stubQueueConflict() {
        crawlerWireMock().stubFor(post(urlEqualTo("/internal/trigger-requests"))
                .willReturn(aResponse().withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Conflict\",\"message\":\"a queued request already exists\"}")));
    }

    private JsonNode lastVerifyRequestBody() {
        List<ServeEvent> events = wireMock().getAllServeEvents();
        try {
            return JSON.readTree(events.get(0).getRequest().getBodyAsString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @TestSecurity(user = "30000000-0000-0000-0000-000000000001", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "30000000-0000-0000-0000-000000000001"))
    @DisplayName("TC-384-J14/AC-08: valid TOTP code -> 202 queued; verify body carries this admin's sub + code")
    void validTotpCodeQueuesTheTrigger() {
        stubVerify(200, "{\"outcome\":\"verified\"}");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl", "code", "123456"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(202)
                .body("status", org.hamcrest.Matchers.equalTo("queued"));

        wireMock().verify(1, postRequestedFor(urlEqualTo(VERIFY_PATH))
                .withRequestBody(matchingJsonPath("$.userId", equalTo("30000000-0000-0000-0000-000000000001")))
                .withRequestBody(matchingJsonPath("$.code", equalTo("123456"))));
    }

    @Test
    @TestSecurity(user = "30000000-0000-0000-0000-000000000002", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "30000000-0000-0000-0000-000000000002"))
    @DisplayName("TC-384-J15/AC-09: valid backup code -> 202 queued")
    void validBackupCodeQueuesTheTrigger() {
        stubVerify(200, "{\"outcome\":\"verified\"}");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl", "code", "AB12CD34"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(202)
                .body("status", org.hamcrest.Matchers.equalTo("queued"));
    }

    @Test
    @TestSecurity(user = "30000000-0000-0000-0000-000000000003", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "30000000-0000-0000-0000-000000000003"))
    @DisplayName("TC-384-J16/AC-12: missing code on a 2FA admin -> 422 'Verification Required', code forwarded as absent/null")
    void missingCodeIsForwardedAsAbsentAndDenied() {
        stubVerify(422, "{\"error\":\"Verification Required\",\"message\":\"code required\"}");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(422)
                .body("error", org.hamcrest.Matchers.equalTo("Verification Required"));

        assertThat(lastVerifyRequestBody().path("code").isNull()).isTrue();
    }

    static Stream<Arguments> invalidVerifyResponses() {
        return Stream.of(
                Arguments.of("wrong digits", "{\"error\":\"Verification Required\",\"message\":\"code invalid\"}"),
                Arguments.of("expired TOTP", "{\"error\":\"Verification Required\",\"message\":\"code expired\"}"),
                Arguments.of("already-used backup code", "{\"error\":\"Verification Required\",\"message\":\"code already used\"}"));
    }

    @ParameterizedTest(name = "TC-384-J17/AC-10/13/14: verify 422 ({0}) -> 422 ''Verification Required''")
    @MethodSource("invalidVerifyResponses")
    @TestSecurity(user = "30000000-0000-0000-0000-000000000004", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "30000000-0000-0000-0000-000000000004"))
    void invalidCodeReturns422RegardlessOfReason(String scenario, String wireMockBody) {
        stubVerify(422, wireMockBody);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl", "code", "000000"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(422)
                .body("error", org.hamcrest.Matchers.equalTo("Verification Required"))
                .body("message", notNullValue());
    }

    @Test
    @TestSecurity(user = "30000000-0000-0000-0000-000000000005", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "30000000-0000-0000-0000-000000000005"))
    @DisplayName("TC-384-J18/AC-16: verify throttled (429) -> 429 propagated")
    void throttledVerifyReturns429() {
        stubVerify(429, "{\"error\":\"Too Many Requests\",\"message\":\"slow down\"}");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl", "code", "123456"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(429);
    }

    @Test
    @TestSecurity(user = "30000000-0000-0000-0000-000000000006", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "30000000-0000-0000-0000-000000000006"))
    @DisplayName("TC-384-J19/AC-17: not-enrolled admin, no code -> 202 queued")
    void notEnrolledAdminWithNoCodeQueuesDirectly() {
        stubVerify(200, "{\"outcome\":\"not_enrolled\"}");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(202)
                .body("status", org.hamcrest.Matchers.equalTo("queued"));
    }

    @Test
    @TestSecurity(user = "30000000-0000-0000-0000-000000000007", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "30000000-0000-0000-0000-000000000007"))
    @DisplayName("TC-384-J20/AC-18/BR-384-5: not-enrolled admin supplies a code anyway -> still 202 queued, ignored")
    void notEnrolledAdminWithCodeSuppliedStillQueues() {
        stubVerify(200, "{\"outcome\":\"not_enrolled\"}");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl", "code", "999999"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(202)
                .body("status", org.hamcrest.Matchers.equalTo("queued"));
    }

    @ParameterizedTest(name = "TC-384-J21/AC-15: malformed code ''{0}'' -> 400, zero verify calls")
    @ValueSource(strings = {"abc", "1234567", "12345"})
    @TestSecurity(user = "30000000-0000-0000-0000-000000000008", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "30000000-0000-0000-0000-000000000008"))
    void malformedCodeRejectedBeforeAnyVerifyCall(String malformedCode) {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl", "code", malformedCode))
                .when().post(TRIGGERS)
                .then()
                .statusCode(400);

        wireMock().verify(0, postRequestedFor(urlEqualTo(VERIFY_PATH)));
    }

    @Test
    @TestSecurity(user = "30000000-0000-0000-0000-000000000009", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "30000000-0000-0000-0000-000000000009"))
    @DisplayName("TC-384-J25/AC-22/BR-384-6: dedupe 409 fires for an authorized 2FA-enrolled admin")
    void dedupeFiresForAuthorizedAdmin() {
        stubVerify(200, "{\"outcome\":\"verified\"}");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl", "code", "123456"))
                .when().post(TRIGGERS)
                .then().statusCode(202);

        stubQueueConflict();

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl", "code", "123456"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(409)
                .body("error", org.hamcrest.Matchers.equalTo("Trigger In Progress"));
    }

    @Test
    @TestSecurity(user = "30000000-0000-0000-0000-000000000010", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "30000000-0000-0000-0000-000000000010"))
    @DisplayName("TC-384-J25/AC-22/BR-384-6: dedupe 409 fires for a not-enrolled admin too")
    void dedupeFiresForNotEnrolledAdmin() {
        stubVerify(200, "{\"outcome\":\"not_enrolled\"}");

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl"))
                .when().post(TRIGGERS)
                .then().statusCode(202);

        stubQueueConflict();

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(409)
                .body("error", org.hamcrest.Matchers.equalTo("Trigger In Progress"));
    }
}
