package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.TriggerDisabledProfile;
import com.davidcreate.jobhub.job.component_tests.support.TwoFactorStubs;
import com.davidcreate.jobhub.job.component_tests.support.WireMockAuthServerResource;
import com.davidcreate.jobhub.job.component_tests.support.WireMockCrawlerServerResource;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Component tests for {@code jobhub.admin.trigger.enabled=false} (Story #7 / ADR 0003).
 *
 * <p>FLAG-2: {@code GET /jobs/admin/triggers/status} must remain reachable (and report
 * {@code triggerEnabled:false}) even when the toggle is off — only the POST is gated.
 *
 * <p>Every admin here stubs as not having 2FA enabled by default (QAE note 0.1).
 * {@link #postNeverReachesTwoFactorGateWhenDisabled()} is TC-384-J22 (BR-384-4, gate
 * order): even with a would-fail-anyway 422 stub, the disabled gate rejects first and
 * the 2FA gate is never called.
 */
@QuarkusTest
@TestProfile(TriggerDisabledProfile.class)
@QuarkusTestResource(WireMockCrawlerServerResource.class)
@DisplayName("Admin Trigger Resource Component Tests — triggering disabled")
class AdminTriggerDisabledComponentTest {

    private static final String TRIGGERS = "/jobs/admin/triggers";
    private static final String STATUS = "/jobs/admin/triggers/status";
    private static final String ADMIN_SUB = "20000000-0000-0000-0000-000000000060";

    @BeforeEach
    void stubNoTwoFactor() {
        TwoFactorStubs.stubNoTwoFactorForEveryAdmin(WireMockAuthServerResource.server());
    }

    @Test
    @TestSecurity(user = ADMIN_SUB, roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = ADMIN_SUB))
    @DisplayName("POST /jobs/admin/triggers → 403 'Triggering Disabled' when the toggle is off")
    void postIsForbiddenWhenDisabled() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(403)
                .body("error", equalTo("Triggering Disabled"))
                .body("message", notNullValue());
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000061", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000061"))
    @DisplayName("GET /jobs/admin/triggers/status remains reachable and reports triggerEnabled=false")
    void statusReachableWhenDisabled() {
        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("triggerEnabled", equalTo(false))
                .body("twoFactorRequired", equalTo(false));
    }

    @Test
    @DisplayName("GET /jobs/admin/triggers/status still requires authentication (401) even when disabled")
    void statusStillRequiresAuthWhenDisabled() {
        given().when().get(STATUS).then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000062", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000062"))
    @DisplayName("AC-18/AC-46 support: GET status returns the most-recent enrichment run from the seeded history "
            + "(succeeded, with resultSummary) — see J-C-23/24/25 for isolated run-info field-shape cases")
    void statusReturnsMostRecentEnrichmentRunFromSeed() {
        // Relocated here from AdminTriggerResourceComponentTest: with triggering disabled,
        // POST is always 403 (FLAG-1), so this profile's DB never receives a fresh
        // trigger_request row — the seeded enrichment history (test-seeds.sql) stays
        // untouched and "most recent" reliably resolves to the seeded `succeeded` row.
        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("enrichment.status", equalTo("succeeded"))
                .body("enrichment.resultSummary", equalTo("enriched 12 postings"))
                .body("enrichment.id", equalTo("99999999-0000-0000-0000-000000000002"));
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000063", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000063"))
    @DisplayName("TC-384-J22/AC-19/BR-384-4: disabled gate wins over the 2FA gate — verify is never called")
    void postNeverReachesTwoFactorGateWhenDisabled() {
        WireMockServer wireMock = WireMockAuthServerResource.server();
        wireMock.resetAll();
        // A stub that would fail the request anyway if called — rules out the stub
        // silently masking an ordering bug (the admin never learns whether their
        // code would have been required, EC-384-4).
        wireMock.stubFor(post(urlEqualTo("/auth/internal/two-factor/verify"))
                .willReturn(aResponse().withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Verification Required\"}")));

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(403)
                .body("error", equalTo("Triggering Disabled"));

        wireMock.verify(0, postRequestedFor(urlEqualTo("/auth/internal/two-factor/verify")));
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000064", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000064"))
    @DisplayName("TR-31: disabled gate fires before crawler-service is ever called → 403, crawler-service sees zero requests")
    void postNeverReachesCrawlerServiceWhenDisabled() {
        WireMockServer crawlerWireMock = WireMockCrawlerServerResource.server();
        crawlerWireMock.resetAll();

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("kind", "crawl"))
                .when().post(TRIGGERS)
                .then()
                .statusCode(403)
                .body("error", equalTo("Triggering Disabled"));

        crawlerWireMock.verify(0, com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching("/internal/trigger-requests.*")));
    }
}
