package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.adapter.out.persistence.entity.TriggerRequestEntity;
import com.davidcreate.jobhub.job.component_tests.support.DedupeStatesProfile;
import com.davidcreate.jobhub.job.component_tests.support.TriggerRequestSeeder;
import com.davidcreate.jobhub.job.component_tests.support.WireMockCrawlerServerResource;
import com.davidcreate.jobhub.job.domain.model.TriggerKind;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Component tests for {@code POST /jobs/admin/triggers/{kind}/cancel}
 * (Story #58 / ADR 0006; moved to a crawler-service call in Story #583 / ADR 0033).
 *
 * <p>Since ADR 0033, job-service's cancel path never writes {@code crawler.trigger_request}
 * itself: it calls crawler-service's internal cancel endpoint and returns whatever that
 * endpoint answers. This class stubs that call via WireMock and asserts on the HTTP
 * response only; seeded rows (via {@link TriggerRequestSeeder}, a native SQL INSERT
 * around the now fully read-only {@code TriggerRequestEntity} mapping) exist only to
 * prove job-service never mutates them.
 */
@QuarkusTest
@TestProfile(DedupeStatesProfile.class)
@QuarkusTestResource(WireMockCrawlerServerResource.class)
@DisplayName("Admin Trigger Cancel Component Tests")
class AdminTriggerCancelComponentTest {

    private static final String ADMIN_SUB = "20000000-0000-0000-0000-000000000100";

    @Inject
    EntityManager entityManager;

    @BeforeEach
    void clearTriggerRequests() {
        QuarkusTransaction.requiringNew()
                .run(() -> entityManager.createQuery("delete from TriggerRequestEntity").executeUpdate());
    }

    @BeforeEach
    void resetCrawlerStubs() {
        wireMock().resetAll();
    }

    private WireMockServer wireMock() {
        return WireMockCrawlerServerResource.server();
    }

    private static String cancelPath(TriggerKind kind) {
        return "/jobs/admin/triggers/" + kind.value() + "/cancel";
    }

    private static String internalCancelPath(TriggerKind kind) {
        return "/internal/trigger-requests/" + kind.value() + "/cancel";
    }

    private void stubCrawlerCancel(TriggerKind kind, int status, String body) {
        wireMock().stubFor(post(urlEqualTo(internalCancelPath(kind)))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private static String triggerResponseJson(UUID id, TriggerKind kind, String status, String origin,
            OffsetDateTime requestedAt, OffsetDateTime finishedAt, String resultSummary) {
        return "{\"id\":\"" + id + "\",\"kind\":\"" + kind.value() + "\",\"status\":\"" + status + "\","
                + "\"origin\":\"" + origin + "\",\"requestedAt\":\"" + requestedAt + "\","
                + "\"finishedAt\":" + (finishedAt == null ? "null" : "\"" + finishedAt + "\"") + ","
                + "\"resultSummary\":" + (resultSummary == null ? "null" : "\"" + resultSummary + "\"") + "}";
    }

    private UUID insertRow(TriggerKind kind, String status, OffsetDateTime requestedAt) {
        OffsetDateTime startedAt = null;
        OffsetDateTime finishedAt = null;
        String resultSummary = null;
        if ("running".equals(status) || "cancel_requested".equals(status)) {
            startedAt = OffsetDateTime.now().minusMinutes(1);
        }
        if ("succeeded".equals(status)) {
            startedAt = requestedAt.plusSeconds(5);
            finishedAt = requestedAt.plusMinutes(1);
            resultSummary = "crawled 5 targets";
        }
        return TriggerRequestSeeder.insert(entityManager, kind.value(), status, requestedAt,
                startedAt, finishedAt, resultSummary, null);
    }

    private TriggerRequestEntity reload(UUID id) {
        return entityManager.find(TriggerRequestEntity.class, id);
    }

    @Test
    @TestSecurity(user = ADMIN_SUB, roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = ADMIN_SUB))
    @DisplayName("JS-COMP-01: 200 cancel a running crawl trigger -> cancel_requested")
    void cancelRunningCrawlTransitionsToCancelRequested() {
        UUID id = insertRow(TriggerKind.CRAWL, "running", OffsetDateTime.now().minusMinutes(2));
        stubCrawlerCancel(TriggerKind.CRAWL, 200, triggerResponseJson(id, TriggerKind.CRAWL, "cancel_requested",
                "manual", OffsetDateTime.now().minusMinutes(2), null, null));

        given()
                .when().post(cancelPath(TriggerKind.CRAWL))
                .then()
                .statusCode(200)
                .body("id", equalTo(id.toString()))
                .body("kind", equalTo("crawl"))
                .body("status", equalTo("cancel_requested"))
                .body("requestedAt", notNullValue());
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000101", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000101"))
    @DisplayName("JS-COMP-02: 200 cancel a queued enrichment trigger -> cancelled (immediate)")
    void cancelQueuedEnrichmentTransitionsImmediatelyToCancelled() {
        UUID id = insertRow(TriggerKind.ENRICHMENT, "queued", OffsetDateTime.now().minusMinutes(2));
        OffsetDateTime finishedAt = OffsetDateTime.now();
        stubCrawlerCancel(TriggerKind.ENRICHMENT, 200, triggerResponseJson(id, TriggerKind.ENRICHMENT, "cancelled",
                "manual", OffsetDateTime.now().minusMinutes(2), finishedAt, "Cancelled before execution"));

        given()
                .when().post(cancelPath(TriggerKind.ENRICHMENT))
                .then()
                .statusCode(200)
                .body("id", equalTo(id.toString()))
                .body("kind", equalTo("enrichment"))
                .body("status", equalTo("cancelled"));
        // TriggerResponse (the public contract) carries only id/kind/status/requestedAt:
        // finishedAt/resultSummary are TriggerRunInfo-only fields, not asserted here.
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000102", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000102"))
    @DisplayName("JS-COMP-03: 409 no active trigger for the kind (crawler-service's internal 404 mapped back)")
    void cancelWithNoTriggerEverIsConflict() {
        stubCrawlerCancel(TriggerKind.ENRICHMENT, 404,
                "{\"error\":\"Not Found\",\"message\":\"No active enrichment request\"}");

        given()
                .when().post(cancelPath(TriggerKind.ENRICHMENT))
                .then()
                .statusCode(409)
                .body("error", equalTo("No Active Trigger"))
                .body("message", notNullValue());

        long count = entityManager
                .createQuery("select count(e) from TriggerRequestEntity e where e.kind = :kind", Long.class)
                .setParameter("kind", TriggerKind.ENRICHMENT.value())
                .getSingleResult();
        assertThat(count).isZero();
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000103", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000103"))
    @DisplayName("JS-COMP-04: 409 most recent trigger for the kind is already terminal (succeeded), row untouched")
    void cancelWithTerminalMostRecentIsConflict() {
        UUID id = insertRow(TriggerKind.CRAWL, "succeeded", OffsetDateTime.now().minusHours(1));
        stubCrawlerCancel(TriggerKind.CRAWL, 404, "{\"error\":\"Not Found\",\"message\":\"No active crawl request\"}");

        given()
                .when().post(cancelPath(TriggerKind.CRAWL))
                .then()
                .statusCode(409)
                .body("error", equalTo("No Active Trigger"))
                .body("message", notNullValue());

        TriggerRequestEntity reloaded = reload(id);
        assertThat(reloaded.status).isEqualTo("succeeded");
        assertThat(reloaded.finishedAt).isNotNull();
        assertThat(reloaded.resultSummary).isEqualTo("crawled 5 targets");
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000104", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000104"))
    @DisplayName("JS-COMP-05: 409 double-cancel: most recent trigger is cancel_requested, row untouched")
    void doubleCancelIsConflict() {
        UUID id = insertRow(TriggerKind.ENRICHMENT, "cancel_requested", OffsetDateTime.now().minusMinutes(5));
        stubCrawlerCancel(TriggerKind.ENRICHMENT, 404,
                "{\"error\":\"Not Found\",\"message\":\"No active enrichment request\"}");

        given()
                .when().post(cancelPath(TriggerKind.ENRICHMENT))
                .then()
                .statusCode(409)
                .body("error", equalTo("No Active Trigger"))
                .body("message", notNullValue());

        TriggerRequestEntity reloaded = reload(id);
        assertThat(reloaded.status).isEqualTo("cancel_requested");
        assertThat(reloaded.finishedAt).isNull();
    }

    @Test
    @DisplayName("JS-COMP-06: 401 missing JWT")
    void cancelWithoutTokenIsUnauthorized() {
        given()
                .when().post(cancelPath(TriggerKind.CRAWL))
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000105", roles = "user")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000105"))
    @DisplayName("JS-COMP-07: 403 authenticated non-admin caller")
    void cancelAsNonAdminIsForbidden() {
        given()
                .when().post(cancelPath(TriggerKind.CRAWL))
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000106", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000106"))
    @DisplayName("JS-COMP-08: cancelling crawl does not affect enrichment's active row (independence of kinds)")
    void cancelOneKindDoesNotAffectTheOther() {
        UUID crawlId = insertRow(TriggerKind.CRAWL, "running", OffsetDateTime.now().minusMinutes(2));
        UUID enrichmentId = insertRow(TriggerKind.ENRICHMENT, "running", OffsetDateTime.now().minusMinutes(2));
        stubCrawlerCancel(TriggerKind.CRAWL, 200, triggerResponseJson(crawlId, TriggerKind.CRAWL, "cancel_requested",
                "manual", OffsetDateTime.now().minusMinutes(2), null, null));

        given()
                .when().post(cancelPath(TriggerKind.CRAWL))
                .then()
                .statusCode(200)
                .body("kind", equalTo("crawl"))
                .body("status", equalTo("cancel_requested"));

        TriggerRequestEntity enrichment = reload(enrichmentId);
        assertThat(enrichment.status).isEqualTo("running");
        assertThat(enrichment.finishedAt).isNull();
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000107", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000107"))
    @DisplayName("TC-513-J14: cancelling via crawler-service never clobbers the local copy of the live progress columns")
    void cancelDoesNotClobberLiveProgressColumns() {
        UUID id = insertRow(TriggerKind.CRAWL, "running", OffsetDateTime.now().minusMinutes(2));
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("UPDATE crawler.trigger_request SET "
                        + "progress_targets_visited = :targetsVisited, "
                        + "progress_new_posts = :newPosts, "
                        + "progress_current_company = :currentCompany, "
                        + "progress_updated_at = :updatedAt "
                        + "WHERE id = :id")
                .setParameter("targetsVisited", 3)
                .setParameter("newPosts", 47)
                .setParameter("currentCompany", "Klaviyo")
                .setParameter("updatedAt", OffsetDateTime.now())
                .setParameter("id", id)
                .executeUpdate());
        stubCrawlerCancel(TriggerKind.CRAWL, 200, triggerResponseJson(id, TriggerKind.CRAWL, "cancel_requested",
                "manual", OffsetDateTime.now().minusMinutes(2), null, null));

        given()
                .when().post(cancelPath(TriggerKind.CRAWL))
                .then()
                .statusCode(200)
                .body("status", equalTo("cancel_requested"));

        TriggerRequestEntity reloaded = reload(id);
        assertThat(reloaded.progressTargetsVisited).isEqualTo(3);
        assertThat(reloaded.progressNewPosts).isEqualTo(47);
        assertThat(reloaded.progressCurrentCompany).isEqualTo("Klaviyo");
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000108", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000108"))
    @DisplayName("TC-513-J15: cancelling a queued (never-reported) crawl still works, progress stays null")
    void cancelQueuedRunKeepsProgressNull() {
        UUID id = insertRow(TriggerKind.CRAWL, "queued", OffsetDateTime.now().minusMinutes(2));
        stubCrawlerCancel(TriggerKind.CRAWL, 200, triggerResponseJson(id, TriggerKind.CRAWL, "cancelled",
                "manual", OffsetDateTime.now().minusMinutes(2), OffsetDateTime.now(), "Cancelled before execution"));

        given()
                .when().post(cancelPath(TriggerKind.CRAWL))
                .then()
                .statusCode(200)
                .body("status", equalTo("cancelled"));

        TriggerRequestEntity reloaded = reload(id);
        assertThat(reloaded.progressUpdatedAt).isNull();
        assertThat(reloaded.progressTargetsVisited).isNull();
    }
}
