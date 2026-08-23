package com.davidcreate.jobhub.crawler.component_tests;

import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.TriggerRequestEntity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Component tests for {@code /internal/trigger-requests} (story #582, ADR 0033).
 *
 * <p>No {@code @TestHTTPEndpoint} with {@code @Nested} (CLAUDE.md rule): a single flat
 * class with a {@code BASE} constant, passed explicitly to RestAssured.
 */
@QuarkusTest
@DisplayName("Internal Trigger Request Resource Component Tests (story #582)")
class InternalTriggerRequestResourceComponentTest {

    private static final String BASE = "/internal/trigger-requests";
    private static final String SERVICE_KEY = "test-internal-key";

    @Inject
    EntityManager entityManager;

    @BeforeEach
    void clearTriggerRequests() {
        QuarkusTransaction.requiringNew().run(() ->
                entityManager.createQuery("delete from TriggerRequestEntity").executeUpdate());
    }

    private UUID insertRow(String kind, String status, OffsetDateTime requestedAt) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            TriggerRequestEntity entity = new TriggerRequestEntity();
            entity.id = id;
            entity.kind = kind;
            entity.status = status;
            entity.origin = "manual";
            entity.requestedAt = requestedAt;
            if ("running".equals(status)) {
                entity.startedAt = requestedAt;
            }
            entityManager.persist(entity);
        });
        return id;
    }

    // ── TR-01/02 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TR-01: no active crawl row - POST queue {kind:crawl}, valid key - 202 queued crawl")
    void queueCrawlWithNoActiveRowReturns202Queued() {
        given()
                .header("X-Service-Key", SERVICE_KEY)
                .contentType("application/json")
                .body(Map.of("kind", "crawl"))
                .when().post(BASE)
                .then()
                .statusCode(202)
                .body("status", org.hamcrest.Matchers.equalTo("queued"))
                .body("kind", org.hamcrest.Matchers.equalTo("crawl"))
                .body("id", notNullValue());
    }

    @Test
    @DisplayName("TR-02: no active enrichment row - POST queue {kind:enrichment}, valid key - "
            + "202 queued enrichment")
    void queueEnrichmentWithNoActiveRowReturns202Queued() {
        given()
                .header("X-Service-Key", SERVICE_KEY)
                .contentType("application/json")
                .body(Map.of("kind", "enrichment"))
                .when().post(BASE)
                .then()
                .statusCode(202)
                .body("status", org.hamcrest.Matchers.equalTo("queued"))
                .body("kind", org.hamcrest.Matchers.equalTo("enrichment"));
    }

    // ── TR-03 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TR-03: a queued row of the kind exists - POST queue same kind - 409, blocked "
            + "by the unique index, no new row")
    void queueingASecondCrawlRequestWhileOneIsQueuedIsConflict() {
        insertRow("crawl", "queued", OffsetDateTime.now());

        given()
                .header("X-Service-Key", SERVICE_KEY)
                .contentType("application/json")
                .body(Map.of("kind", "crawl"))
                .when().post(BASE)
                .then()
                .statusCode(409)
                .body("error", notNullValue())
                .body("message", notNullValue());

        long count = QuarkusTransaction.requiringNew().call(() ->
                (Long) entityManager.createQuery("select count(t) from TriggerRequestEntity t where t.kind = 'crawl'")
                        .getSingleResult());
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    // ── TR-05/06/07 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("TR-05: a queued row is seeded - POST cancel, valid key - 200, status cancelled, "
            + "finishedAt set")
    void cancellingAQueuedRowReturnsCancelledWithFinishedAt() {
        insertRow("crawl", "queued", OffsetDateTime.now().minusMinutes(5));

        given()
                .header("X-Service-Key", SERVICE_KEY)
                .when().post(BASE + "/crawl/cancel")
                .then()
                .statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo("cancelled"))
                .body("finishedAt", notNullValue());
    }

    @Test
    @DisplayName("TR-06: a running row is seeded - POST cancel, valid key - 200, status "
            + "cancel_requested")
    void cancellingARunningRowReturnsCancelRequested() {
        insertRow("enrichment", "running", OffsetDateTime.now().minusMinutes(5));

        given()
                .header("X-Service-Key", SERVICE_KEY)
                .when().post(BASE + "/enrichment/cancel")
                .then()
                .statusCode(200)
                .body("status", org.hamcrest.Matchers.equalTo("cancel_requested"))
                .body("finishedAt", nullValue());
    }

    @Test
    @DisplayName("TR-07: no active row of the kind - POST cancel, valid key - 404, no row changed")
    void cancellingWithNoActiveRowReturns404() {
        given()
                .header("X-Service-Key", SERVICE_KEY)
                .when().post(BASE + "/crawl/cancel")
                .then()
                .statusCode(404)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // ── TR-11/12: validation ─────────────────────────────────────────────────

    @Test
    @DisplayName("TR-11a: an unknown 'kind' value on queue -> 400 {error, message}")
    void queueWithUnknownKindIsBadRequest() {
        given()
                .header("X-Service-Key", SERVICE_KEY)
                .contentType("application/json")
                .body("{\"kind\":\"bogus\"}")
                .when().post(BASE)
                .then()
                .statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    @Test
    @DisplayName("TR-11b: a malformed 'requestedBy' uuid on queue -> 400 {error, message}")
    void queueWithMalformedRequestedByIsBadRequest() {
        given()
                .header("X-Service-Key", SERVICE_KEY)
                .contentType("application/json")
                .body("{\"kind\":\"crawl\",\"requestedBy\":\"not-a-uuid\"}")
                .when().post(BASE)
                .then()
                .statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    @Test
    @DisplayName("TR-11c: a missing request body on queue -> 400")
    void queueWithMissingBodyIsBadRequest() {
        given()
                .header("X-Service-Key", SERVICE_KEY)
                .contentType("application/json")
                .when().post(BASE)
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("TR-12: an unknown 'kind' path segment on cancel -> 400")
    void cancelWithUnknownKindPathSegmentIsBadRequest() {
        given()
                .header("X-Service-Key", SERVICE_KEY)
                .when().post(BASE + "/bogus/cancel")
                .then()
                .statusCode(400)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }
}
