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
 * Component tests for the live crawl progress exposed on
 * {@code GET /jobs/admin/triggers/status} (Story #513 / ADR 0029, TC-513-J5..J11).
 *
 * <p>Reuses {@link StatusRunDetailsProfile} (its own fresh {@code drop-and-create}
 * DB, independent of {@code test-seeds.sql}'s enrichment history), seeding rows
 * programmatically via {@link QuarkusTransaction#requiringNew()} +
 * {@link EntityManager}, same precedent as {@link AdminTriggerStatusRunDetailsComponentTest}.
 *
 * <p>job-service's {@code TriggerRequestEntity} maps the nine {@code progress_*}
 * columns {@code insertable = false, updatable = false} (ADR 0029 decision 4), so
 * seeding them here goes through a native SQL UPDATE rather than the mapped
 * entity's own insert path: this is a test-seeding technique only, it does not
 * touch production code.
 */
@QuarkusTest
@TestProfile(StatusRunDetailsProfile.class)
@QuarkusTestResource(WireMockAuthServerResource.class)
@DisplayName("Admin Trigger Status Component Tests: live crawl progress (Story #513)")
class AdminTriggerStatusProgressComponentTest {

    private static final String STATUS = "/jobs/admin/triggers/status";

    @Inject
    EntityManager entityManager;

    @BeforeEach
    void stubNoTwoFactor() {
        TwoFactorStubs.stubNoTwoFactorForEveryAdmin(WireMockAuthServerResource.server());
    }

    // Each test seeds its own single crawl row with a fixed requestedAt offset
    // (e.g. "1 hour ago" for terminal fixtures) so it must win findMostRecent's
    // requestedAt-descending ordering regardless of what an earlier test in this
    // class already committed to the shared drop-and-create DB.
    @BeforeEach
    void clearTriggerRequests() {
        QuarkusTransaction.requiringNew()
                .run(() -> entityManager.createQuery("delete from TriggerRequestEntity").executeUpdate());
    }

    private UUID insertCrawlRow(String status, OffsetDateTime requestedAt) {
        OffsetDateTime startedAt = null;
        OffsetDateTime finishedAt = null;
        if ("running".equals(status) || "cancel_requested".equals(status)) {
            startedAt = requestedAt.plusSeconds(5);
        } else if (!"queued".equals(status)) {
            startedAt = requestedAt.plusSeconds(5);
            finishedAt = requestedAt.plusMinutes(1);
        }
        return TriggerRequestSeeder.insert(entityManager, TriggerKind.CRAWL.value(), status, requestedAt,
                startedAt, finishedAt, null, null);
    }

    private void setResultText(UUID id, String resultSummary, String errorReason) {
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("UPDATE crawler.trigger_request SET result_summary = :resultSummary, "
                        + "error_reason = :errorReason WHERE id = :id")
                .setParameter("resultSummary", resultSummary)
                .setParameter("errorReason", errorReason)
                .setParameter("id", id)
                .executeUpdate());
    }

    private void setProgress(UUID id, Integer targetsVisited, Integer newPosts, String currentCompany,
            String currentSourceType, String lastCompany, String lastSourceType, Integer lastFoundPosts,
            Integer lastNewPosts, OffsetDateTime updatedAt) {
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("UPDATE crawler.trigger_request SET "
                        + "progress_targets_visited = :targetsVisited, "
                        + "progress_new_posts = :newPosts, "
                        + "progress_current_company = :currentCompany, "
                        + "progress_current_source_type = :currentSourceType, "
                        + "progress_last_company = :lastCompany, "
                        + "progress_last_source_type = :lastSourceType, "
                        + "progress_last_found_posts = :lastFoundPosts, "
                        + "progress_last_new_posts = :lastNewPosts, "
                        + "progress_updated_at = :updatedAt "
                        + "WHERE id = :id")
                .setParameter("targetsVisited", targetsVisited)
                .setParameter("newPosts", newPosts)
                .setParameter("currentCompany", currentCompany)
                .setParameter("currentSourceType", currentSourceType)
                .setParameter("lastCompany", lastCompany)
                .setParameter("lastSourceType", lastSourceType)
                .setParameter("lastFoundPosts", lastFoundPosts)
                .setParameter("lastNewPosts", lastNewPosts)
                .setParameter("updatedAt", updatedAt)
                .setParameter("id", id)
                .executeUpdate());
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000110", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000110"))
    @DisplayName("TC-513-J5: running crawl mid-run: all nine progress fields exposed exactly")
    void runningCrawlMidRunExposesAllProgressFields() {
        UUID id = insertCrawlRow("running", OffsetDateTime.now().minusMinutes(2));
        OffsetDateTime updatedAt = OffsetDateTime.now().minusSeconds(10);
        setProgress(id, 3, 47, "Klaviyo", "greenhouse", "Stripe", "lever", 142, 16, updatedAt);

        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("crawl.progress.targetsVisited", equalTo(3))
                .body("crawl.progress.newPosts", equalTo(47))
                .body("crawl.progress.currentCompany", equalTo("Klaviyo"))
                .body("crawl.progress.currentSourceType", equalTo("greenhouse"))
                .body("crawl.progress.lastCompany", equalTo("Stripe"))
                .body("crawl.progress.lastSourceType", equalTo("lever"))
                .body("crawl.progress.lastFoundPosts", equalTo(142))
                .body("crawl.progress.lastNewPosts", equalTo(16))
                .body("crawl.progress.updatedAt", notNullValue());
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000111", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000111"))
    @DisplayName("TC-513-J6: queued crawl: progress is null")
    void queuedCrawlHasNullProgress() {
        insertCrawlRow("queued", OffsetDateTime.now().minusMinutes(1));

        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("crawl.progress", nullValue())
                .body("crawl.status", equalTo("queued"));
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000112", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000112"))
    @DisplayName("TC-513-J7: running crawl before its first report: progress is still null")
    void runningCrawlBeforeFirstReportHasNullProgress() {
        insertCrawlRow("running", OffsetDateTime.now().minusSeconds(30));

        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("crawl.status", equalTo("running"))
                .body("crawl.progress", nullValue());
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000113", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000113"))
    @DisplayName("TC-513-J8: terminal succeeded crawl: counters remain, currentCompany/currentSourceType are null")
    void terminalSucceededCrawlKeepsCountersClearsCurrent() {
        UUID id = insertCrawlRow("succeeded", OffsetDateTime.now().minusHours(1));
        setResultText(id, "Batch complete: 5 targets visited, 0 new posts", null);
        setProgress(id, 5, 0, null, null, "Figma", "greenhouse", 30, 0, OffsetDateTime.now().minusMinutes(50));

        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("crawl.progress.targetsVisited", equalTo(5))
                .body("crawl.progress.newPosts", equalTo(0))
                .body("crawl.progress.currentCompany", nullValue())
                .body("crawl.resultSummary", equalTo("Batch complete: 5 targets visited, 0 new posts"))
                .body("crawl.errorReason", nullValue());
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000114", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000114"))
    @DisplayName("TC-513-J9: terminal cancelled crawl (settled): same shape as J8, cancelled resultSummary text")
    void terminalCancelledCrawlKeepsCountersClearsCurrent() {
        UUID id = insertCrawlRow("cancelled", OffsetDateTime.now().minusHours(1));
        setResultText(id, "Batch cancelled: 4 targets visited, 30 new posts before stop", null);
        setProgress(id, 4, 30, null, null, "Acme", "lever", 8, 2, OffsetDateTime.now().minusMinutes(40));

        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("crawl.progress.currentCompany", nullValue())
                .body("crawl.resultSummary", equalTo("Batch cancelled: 4 targets visited, 30 new posts before stop"));
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000115", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000115"))
    @DisplayName("TC-513-J10: terminal failed crawl: progress counters remain, errorReason populated")
    void terminalFailedCrawlKeepsCountersAndErrorReason() {
        UUID id = insertCrawlRow("failed", OffsetDateTime.now().minusHours(1));
        setResultText(id, null, "connection refused");
        setProgress(id, 4, 12, null, null, "NextCo", "workday", 0, 0, OffsetDateTime.now().minusMinutes(30));

        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("crawl.progress.targetsVisited", equalTo(4))
                .body("crawl.errorReason", equalTo("connection refused"))
                .body("crawl.resultSummary", nullValue());
    }

    @Test
    @TestSecurity(user = "20000000-0000-0000-0000-000000000116", roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = "20000000-0000-0000-0000-000000000116"))
    @DisplayName("TC-513-J11: failed target mid-run: targetsVisited advances, lastFoundPosts/lastNewPosts are 0, no errorReason")
    void failedTargetMidRunAdvancesCountersWithoutErrorReason() {
        UUID id = insertCrawlRow("running", OffsetDateTime.now().minusMinutes(3));
        setProgress(id, 4, 30, "NextCo", "smartrecruiters", "Acme", "greenhouse", 0, 0,
                OffsetDateTime.now().minusSeconds(5));

        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("crawl.status", equalTo("running"))
                .body("crawl.progress.targetsVisited", equalTo(4))
                .body("crawl.progress.newPosts", equalTo(30))
                .body("crawl.progress.lastFoundPosts", equalTo(0))
                .body("crawl.progress.lastNewPosts", equalTo(0))
                .body("crawl.errorReason", nullValue());
    }
}
