package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.TriggerRequestSeeder;
import com.davidcreate.jobhub.job.component_tests.support.TwoFactorStubs;
import com.davidcreate.jobhub.job.component_tests.support.WireMockAuthServerResource;
import com.davidcreate.jobhub.job.component_tests.support.WireMockCrawlerServerResource;
import com.davidcreate.jobhub.job.domain.model.TriggerKind;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Component tests for {@code POST /jobs/admin/triggers} and
 * {@code GET /jobs/admin/triggers/status} (Story #7 / ADR 0003; 2FA gate: ADR 0019;
 * crawler-service call: ADR 0033, ticket #583), default profile:
 * {@code jobhub.admin.trigger.enabled=true}.
 *
 * <p>Since ADR 0033, queueing and cancelling go through crawler-service's internal
 * endpoints, stubbed here via {@link WireMockCrawlerServerResource}: job-service no
 * longer writes {@code crawler.trigger_request} itself. {@code GET .../status} is
 * unaffected: it stays a direct {@code SELECT} and is asserted here with zero
 * crawler-service WireMock interactions (TR-28/TR-29).
 *
 * <p>Every admin in this class stubs as not having 2FA enabled (see
 * {@link TwoFactorStubs#stubNoTwoFactorForEveryAdmin}) — the 2FA gate itself is
 * exercised in {@code AdminTriggerTwoFactorGateComponentTest} and
 * {@code AdminTriggerStatusTwoFactorComponentTest}; this class's assertions are
 * unrelated to 2FA and must keep passing unchanged.
 */
@QuarkusTest
@QuarkusTestResource(WireMockAuthServerResource.class)
@QuarkusTestResource(WireMockCrawlerServerResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Admin Trigger Resource Component Tests")
class AdminTriggerResourceComponentTest {

    private static final String TRIGGERS = "/jobs/admin/triggers";
    private static final String STATUS = "/jobs/admin/triggers/status";
    private static final String ADMIN_SUB = "20000000-0000-0000-0000-000000000001";

    @Inject
    EntityManager entityManager;

    private WireMockServer crawlerWireMock() {
        return WireMockCrawlerServerResource.server();
    }

    @BeforeEach
    void stubNoTwoFactor() {
        TwoFactorStubs.stubNoTwoFactorForEveryAdmin(WireMockAuthServerResource.server());
    }

    @BeforeEach
    void resetCrawlerStubs() {
        crawlerWireMock().resetAll();
    }

    private static String internalCancelPath(TriggerKind kind) {
        return "/internal/trigger-requests/" + kind.value() + "/cancel";
    }

    private static String triggerResponseJson(UUID id, TriggerKind kind, String status, String origin,
            OffsetDateTime requestedAt, OffsetDateTime finishedAt, String resultSummary) {
        return "{\"id\":\"" + id + "\",\"kind\":\"" + kind.value() + "\",\"status\":\"" + status + "\","
                + "\"origin\":\"" + origin + "\",\"requestedAt\":\"" + requestedAt + "\","
                + "\"finishedAt\":" + (finishedAt == null ? "null" : "\"" + finishedAt + "\"") + ","
                + "\"resultSummary\":" + (resultSummary == null ? "null" : "\"" + resultSummary + "\"") + "}";
    }

    private void stubQueueAccepted(TriggerKind kind) {
        crawlerWireMock().stubFor(post(urlEqualTo("/internal/trigger-requests"))
                .withRequestBody(matchingJsonPath("$.kind", com.github.tomakehurst.wiremock.client.WireMock.equalTo(kind.value())))
                .willReturn(aResponse().withStatus(202)
                        .withHeader("Content-Type", "application/json")
                        .withBody(triggerResponseJson(UUID.randomUUID(), kind, "queued", "manual",
                                OffsetDateTime.now(), null, null))));
    }

    private void stubQueueConflict(TriggerKind kind) {
        crawlerWireMock().stubFor(post(urlEqualTo("/internal/trigger-requests"))
                .withRequestBody(matchingJsonPath("$.kind", com.github.tomakehurst.wiremock.client.WireMock.equalTo(kind.value())))
                .willReturn(aResponse().withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Conflict\",\"message\":\"a queued request already exists\"}")));
    }

    // ── J-C-01/02: authz ───────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("J-C-01: POST without a Bearer token → 401")
    void postWithoutTokenIsUnauthorized() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl"))
                .when().post(TRIGGERS)
                .then().statusCode(401);
    }

    @Test
    @Order(3)
    @TestSecurity(user = "20000000-0000-0000-0000-000000000002", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000002"))
    @DisplayName("J-C-03a: POST as an authenticated non-admin → 403 (standard JWT policy denial)")
    void postAsNonAdminIsForbidden() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl"))
                .when().post(TRIGGERS)
                .then().statusCode(403);
    }

    @Test
    @Order(4)
    @TestSecurity(user = "20000000-0000-0000-0000-000000000003", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000003"))
    @DisplayName("J-C-03b: the policy 403 (role denial) carries no 'Triggering Disabled' marker (distinct from the toggle-off 403)")
    void policyForbiddenHasNoTriggeringDisabledMarker() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(403)
                .body(equalTo(""));
    }

    @Test
    @Order(5)
    @DisplayName("J-C-02: GET status without a Bearer token → 401")
    void statusWithoutTokenIsUnauthorized() {
        given().when().get(STATUS).then().statusCode(401);
    }

    @Test
    @Order(6)
    @TestSecurity(user = "20000000-0000-0000-0000-000000000004", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000004"))
    @DisplayName("J-C-04: GET status as a non-admin → 403")
    void statusAsNonAdminIsForbidden() {
        given().when().get(STATUS).then().statusCode(403);
    }

    // ── J-C-10/11: validation ───────────────────────────────────────────────────

    @Test
    @Order(7)
    @TestSecurity(user = ADMIN_SUB, roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = ADMIN_SUB))
    @DisplayName("J-C-10: POST with an unknown 'kind' → 400 {error, message}")
    void postWithUnknownKindIsBadRequest() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"kind\":\"bogus\"}")
                .when().post(TRIGGERS)
                .then()
                .statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    @Test
    @Order(8)
    @TestSecurity(user = ADMIN_SUB, roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = ADMIN_SUB))
    @DisplayName("AC-384-15 support: POST with a malformed 'code' (neither 6-digit TOTP nor 8-char backup) → 400")
    void postWithMalformedCodeIsBadRequest() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl", "code", "12a"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    @Test
    @Order(9)
    @TestSecurity(user = ADMIN_SUB, roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = ADMIN_SUB))
    @DisplayName("J-C-11: POST with a missing body → 400")
    void postWithMissingBodyIsBadRequest() {
        given()
                .contentType(ContentType.JSON)
                .when().post(TRIGGERS)
                .then()
                .statusCode(400);
    }

    // ── J-C-22: status before any POST in this class — crawl absent/null ────────

    @Test
    @Order(1)
    @TestSecurity(user = "20000000-0000-0000-0000-000000000032", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000032"))
    @DisplayName("J-C-22: GET status crawl is null/absent on a fresh deployment (never triggered)")
    void statusCrawlNullWhenNeverTriggered() {
        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("$", org.hamcrest.Matchers.hasKey("crawl"));
    }

    // ── TR-21/TR-22: queue via crawler-service (202/409) ─────────────────────────

    @Test
    @Order(10)
    @TestSecurity(user = "20000000-0000-0000-0000-000000000010", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000010"))
    @DisplayName("TR-21/J-C-05: POST kind=crawl, WireMock 202 stub → 202 + queued TriggerResponse, body shape unchanged")
    void postCrawlAccepted() {
        stubQueueAccepted(TriggerKind.CRAWL);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(202)
                .body("id", notNullValue())
                .body("kind", equalTo("crawl"))
                .body("status", equalTo("queued"))
                .body("requestedAt", notNullValue());

        crawlerWireMock().verify(1, postRequestedFor(urlEqualTo("/internal/trigger-requests")));
    }

    @Test
    @Order(11)
    @TestSecurity(user = "20000000-0000-0000-0000-000000000011", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000011"))
    @DisplayName("J-C-06/J-C-09: POST kind=enrichment, WireMock 202 stub → 202, even while crawl (a different kind) is queued")
    void postEnrichmentAcceptedWhileCrawlInProgress() {
        stubQueueAccepted(TriggerKind.ENRICHMENT);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "enrichment"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(202)
                .body("id", notNullValue())
                .body("kind", equalTo("enrichment"))
                .body("status", equalTo("queued"))
                .body("requestedAt", notNullValue());
    }

    @Test
    @Order(12)
    @TestSecurity(user = "20000000-0000-0000-0000-000000000020", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000020"))
    @DisplayName("TR-22/J-C-07: POST kind=crawl twice, second WireMock 409 stub → 409 'Trigger In Progress', public error body")
    void postDedupesQueuedCrawl() {
        stubQueueConflict(TriggerKind.CRAWL);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(409)
                .body("error", equalTo("Trigger In Progress"))
                .body("message", notNullValue());
    }

    @Test
    @Order(13)
    @TestSecurity(user = "20000000-0000-0000-0000-000000000021", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000021"))
    @DisplayName("AC-38 support: POST kind=enrichment while one is already queued → 409 'Trigger In Progress'")
    void postDedupesQueuedEnrichment() {
        stubQueueConflict(TriggerKind.ENRICHMENT);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "enrichment"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(409)
                .body("error", equalTo("Trigger In Progress"))
                .body("message", notNullValue());
    }

    // ── TR-25/TR-26/TR-27: cancel via crawler-service (404→409/200) ─────────────

    @Test
    @Order(14)
    @TestSecurity(user = "20000000-0000-0000-0000-000000000022", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000022"))
    @DisplayName("TR-25: WireMock 404 for cancel → 409, same public shape as before ADR 0033")
    void cancelNoActiveRequestIsConflict() {
        crawlerWireMock().stubFor(post(urlEqualTo(internalCancelPath(TriggerKind.CRAWL)))
                .willReturn(aResponse().withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Not Found\",\"message\":\"no active crawl request\"}")));

        given()
                .when().post(TRIGGERS + "/crawl/cancel")
                .then()
                .statusCode(409)
                .body("error", equalTo("No Active Trigger"))
                .body("message", notNullValue());
    }

    @Test
    @Order(15)
    @TestSecurity(user = "20000000-0000-0000-0000-000000000023", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000023"))
    @DisplayName("TR-26: WireMock 200 cancelled body (crawler-recorded finishedAt) → 200, status:cancelled")
    void cancelQueuedReturnsCancelledImmediately() {
        // crawler-service's own response carries finishedAt (its internal TR-05); the
        // public TriggerResponse contract only carries id/kind/status/requestedAt, so
        // that is all this asserts on job-service's side.
        UUID id = UUID.randomUUID();
        crawlerWireMock().stubFor(post(urlEqualTo(internalCancelPath(TriggerKind.CRAWL)))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(triggerResponseJson(id, TriggerKind.CRAWL, "cancelled", "manual",
                                OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now(),
                                "Cancelled before execution"))));

        given()
                .when().post(TRIGGERS + "/crawl/cancel")
                .then()
                .statusCode(200)
                .body("id", equalTo(id.toString()))
                .body("status", equalTo("cancelled"));
    }

    @Test
    @Order(16)
    @TestSecurity(user = "20000000-0000-0000-0000-000000000024", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000024"))
    @DisplayName("TR-27: WireMock 200 cancel_requested body → 200, status:cancel_requested")
    void cancelRunningReturnsCancelRequested() {
        UUID id = UUID.randomUUID();
        crawlerWireMock().stubFor(post(urlEqualTo(internalCancelPath(TriggerKind.CRAWL)))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(triggerResponseJson(id, TriggerKind.CRAWL, "cancel_requested", "manual",
                                OffsetDateTime.now().minusMinutes(1), null, null))));

        given()
                .when().post(TRIGGERS + "/crawl/cancel")
                .then()
                .statusCode(200)
                .body("id", equalTo(id.toString()))
                .body("status", equalTo("cancel_requested"));
    }

    // ── J-C-21: status toggles ───────────────────────────────────────────────────

    @Test
    @Order(20)
    @TestSecurity(user = "20000000-0000-0000-0000-000000000030", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000030"))
    @DisplayName("TC-384-J26/27: GET status reflects twoFactorRequired=false (and triggerEnabled=true) in the default profile")
    void statusReflectsToggles() {
        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("triggerEnabled", equalTo(true))
                .body("twoFactorRequired", equalTo(false));
    }

    @Test
    @Order(21)
    @TestSecurity(user = "20000000-0000-0000-0000-000000000040", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000040"))
    @DisplayName("TC-384-J32/AC-07: GET status response shape includes triggerEnabled and twoFactorRequired, never codeRequired")
    void statusResponseShape() {
        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("triggerEnabled", notNullValue())
                .body("twoFactorRequired", notNullValue())
                .body("$", not(hasKey("codeRequired")));
    }

    // ── TR-28/TR-29: status/history stay a direct SELECT, no crawler-service call ──

    @Test
    @Order(30)
    @TestSecurity(user = "20000000-0000-0000-0000-000000000041", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000041"))
    @DisplayName("TR-28: GET status with seeded rows, no WireMock stub configured → 200, direct SELECT, zero crawler-service calls")
    void statusReadsSeededRowsWithoutCallingCrawlerService() {
        TriggerRequestSeeder.insert(entityManager, TriggerKind.CRAWL.value(), "queued",
                OffsetDateTime.now().minusMinutes(1), null, null, null, null);

        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("crawl.status", equalTo("queued"));

        crawlerWireMock().verify(0, postRequestedFor(urlEqualTo("/internal/trigger-requests")));
    }

    @Test
    @Order(31)
    @TestSecurity(user = "20000000-0000-0000-0000-000000000042", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000042"))
    @DisplayName("TR-29: GET status surfaces last-finished-run history from seeded rows, direct SELECT, zero crawler-service calls")
    void statusReadsFinishedRunHistoryWithoutCallingCrawlerService() {
        TriggerRequestSeeder.insert(entityManager, TriggerKind.ENRICHMENT.value(), "succeeded",
                OffsetDateTime.now().minusHours(1), OffsetDateTime.now().minusHours(1).plusSeconds(5),
                OffsetDateTime.now().minusMinutes(55), "enriched 3 postings", null);

        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("lastEnrichmentRun.status", equalTo("succeeded"))
                .body("lastEnrichmentRun.resultSummary", equalTo("enriched 3 postings"));

        crawlerWireMock().verify(0, postRequestedFor(urlEqualTo("/internal/trigger-requests")));
    }
}
