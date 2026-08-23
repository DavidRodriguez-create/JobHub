package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.StatusRunDetailsProfile;
import com.davidcreate.jobhub.job.component_tests.support.TriggerRequestSeeder;
import com.davidcreate.jobhub.job.component_tests.support.TwoFactorStubs;
import com.davidcreate.jobhub.job.component_tests.support.WireMockAuthServerResource;
import com.davidcreate.jobhub.job.domain.model.TriggerKind;
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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Component tests for {@code GET /jobs/admin/triggers/status} run-info field
 * shape (Story #7 / ADR 0003 — QA end-review gaps J-C-24/J-C-25).
 *
 * <p>Runs in its own {@link StatusRunDetailsProfile} so it gets a fresh
 * {@code drop-and-create} DB independent of the seeded enrichment history in
 * {@code test-seeds.sql} (which makes the {@code succeeded} enrichment row the
 * newest — the inverse of what J-C-25 needs).
 *
 * <p>Every admin here stubs as not having 2FA enabled (QAE note 0.1) — unrelated to
 * this class's run-info field-shape assertions.
 */
@QuarkusTest
@TestProfile(StatusRunDetailsProfile.class)
@QuarkusTestResource(WireMockAuthServerResource.class)
@DisplayName("Admin Trigger Status Component Tests — run-info field shape")
class AdminTriggerStatusRunDetailsComponentTest {

    private static final String STATUS = "/jobs/admin/triggers/status";
    private static final String ADMIN_SUB = "20000000-0000-0000-0000-000000000096";

    @Inject
    EntityManager entityManager;

    @BeforeEach
    void seedRows() {
        // Story #513: AdminTriggerStatusProgressComponentTest shares this same
        // StatusRunDetailsProfile config, so Quarkus test tooling reuses the same
        // running app/DB across both classes rather than restarting per class.
        // Clear first so this class's fixed-offset fixtures always win
        // findMostRecent's requestedAt-descending ordering regardless of what the
        // other class's tests left behind.
        QuarkusTransaction.requiringNew()
                .run(() -> entityManager.createQuery("delete from TriggerRequestEntity").executeUpdate());

        // J-C-24: a single succeeded crawl run with a populated result summary.
        UUID crawlId = TriggerRequestSeeder.insert(entityManager, TriggerKind.CRAWL.value(), "succeeded",
                OffsetDateTime.now().minusHours(1), OffsetDateTime.now().minusHours(1).plusSeconds(5),
                OffsetDateTime.now().minusMinutes(50), "crawled 5 targets", null);
        // C26: origin/outcome are insertable=false/updatable=false on the entity
        // (job-service is not their writer, see TriggerRequestEntity) so this
        // fixture sets them the same way crawler-service would: a raw UPDATE.
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(
                        "update crawler.trigger_request set origin = 'scheduled', outcome = 'completed' where id = ?1")
                .setParameter(1, crawlId)
                .executeUpdate());

        // J-C-25: an older succeeded enrichment run, and a more recent failed one
        // (the failed run must be the one `status` reports as most-recent).
        TriggerRequestSeeder.insert(entityManager, TriggerKind.ENRICHMENT.value(), "succeeded",
                OffsetDateTime.now().minusHours(2), OffsetDateTime.now().minusHours(2).plusSeconds(5),
                OffsetDateTime.now().minusHours(1).minusMinutes(50), "enriched 8 postings", null);

        UUID newerFailedId = TriggerRequestSeeder.insert(entityManager, TriggerKind.ENRICHMENT.value(), "failed",
                OffsetDateTime.now().minusMinutes(30), OffsetDateTime.now().minusMinutes(30).plusSeconds(5),
                OffsetDateTime.now().minusMinutes(28), null, "connection refused");
        // C26: newerFailed is the newest terminal ENRICHMENT row, so it is the
        // one lastEnrichmentRun must surface, with a manual origin.
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(
                        "update crawler.trigger_request set origin = 'manual', outcome = 'failed' where id = ?1")
                .setParameter(1, newerFailedId)
                .executeUpdate());
    }

    @BeforeEach
    void stubNoTwoFactor() {
        TwoFactorStubs.stubNoTwoFactorForEveryAdmin(WireMockAuthServerResource.server());
    }

    @Test
    @TestSecurity(user = ADMIN_SUB, roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = ADMIN_SUB))
    @DisplayName("J-C-24: GET status reflects a succeeded crawl run with resultSummary, timestamps, and no errorReason")
    void statusReflectsSucceededCrawlRun() {
        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("crawl.status", equalTo("succeeded"))
                .body("crawl.startedAt", notNullValue())
                .body("crawl.finishedAt", notNullValue())
                .body("crawl.resultSummary", equalTo("crawled 5 targets"))
                .body("crawl.errorReason", nullValue())
                // TC-513-J12: a pre-feature run (no progress_* columns ever set) exposes progress = null.
                .body("crawl.progress", nullValue());
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000097", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000097"))
    @DisplayName("J-C-25: GET status reflects a failed-most-recent enrichment run with errorReason and no resultSummary")
    void statusReflectsFailedEnrichmentRun() {
        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("enrichment.status", equalTo("failed"))
                .body("enrichment.errorReason", equalTo("connection refused"))
                .body("enrichment.resultSummary", nullValue())
                // TC-513-J13: enrichment never reports progress, at any status.
                .body("enrichment.progress", nullValue());
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000098", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000098"))
    @DisplayName("C26: GET status lastCrawlRun/lastEnrichmentRun carry origin, outcome, and finishedAt for finished runs")
    void statusReflectsLastRunOriginOutcomeFinishedAt() {
        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("lastCrawlRun.status", equalTo("succeeded"))
                .body("lastCrawlRun.origin", equalTo("scheduled"))
                .body("lastCrawlRun.outcome", equalTo("completed"))
                .body("lastCrawlRun.finishedAt", notNullValue())
                .body("lastEnrichmentRun.status", equalTo("failed"))
                .body("lastEnrichmentRun.origin", equalTo("manual"))
                .body("lastEnrichmentRun.outcome", equalTo("failed"))
                .body("lastEnrichmentRun.finishedAt", notNullValue());
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000099", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000099"))
    @DisplayName("C27: GET status lastCrawlRun is null when CRAWL has never finished a run, 200 not a crash")
    void statusLastCrawlRunNullWhenNeverFinished() {
        QuarkusTransaction.requiringNew()
                .run(() -> entityManager.createQuery("delete from TriggerRequestEntity where kind = 'crawl'")
                        .executeUpdate());

        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("$", org.hamcrest.Matchers.hasKey("lastCrawlRun"))
                .body("lastCrawlRun", nullValue())
                .body("lastEnrichmentRun.status", equalTo("failed"));
    }
}
