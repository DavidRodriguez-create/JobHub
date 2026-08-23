package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.StatusOrderingProfile;
import com.davidcreate.jobhub.job.component_tests.support.TriggerRequestSeeder;
import com.davidcreate.jobhub.job.component_tests.support.TwoFactorStubs;
import com.davidcreate.jobhub.job.component_tests.support.WireMockAuthServerResource;
import com.davidcreate.jobhub.job.domain.model.TriggerKind;
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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Component test for {@code GET /jobs/admin/triggers/status} most-recent-per-kind
 * ordering (Story #7 / ADR 0003 — QA end-review gap J-C-23).
 *
 * <p>Runs in its own {@link StatusOrderingProfile} so it gets a fresh
 * {@code drop-and-create} DB independent of the seeded enrichment history in
 * {@code test-seeds.sql} and of other component test classes' inserted rows.
 *
 * <p>The admin here stubs as not having 2FA enabled (QAE note 0.1) — unrelated to
 * this class's most-recent-per-kind ordering assertion.
 */
@QuarkusTest
@TestProfile(StatusOrderingProfile.class)
@QuarkusTestResource(WireMockAuthServerResource.class)
@DisplayName("Admin Trigger Status Component Tests — most-recent-per-kind ordering")
class AdminTriggerStatusOrderingComponentTest {

    private static final String STATUS = "/jobs/admin/triggers/status";
    private static final String ADMIN_SUB = "20000000-0000-0000-0000-000000000095";

    @Inject
    EntityManager entityManager;

    private void insertRow(TriggerKind kind, String status, OffsetDateTime requestedAt) {
        TriggerRequestSeeder.insert(entityManager, kind.value(), status, requestedAt,
                requestedAt.plusSeconds(5), requestedAt.plusMinutes(1), null, null);
    }

    @BeforeEach
    void seedRows() {
        // Row A: older succeeded crawl run.
        insertRow(TriggerKind.CRAWL, "succeeded", OffsetDateTime.now().minusHours(2));
        // Row B: more recent failed crawl run — this is the one `status` must report.
        insertRow(TriggerKind.CRAWL, "failed", OffsetDateTime.now().minusHours(1));
        // Single succeeded enrichment run.
        insertRow(TriggerKind.ENRICHMENT, "succeeded", OffsetDateTime.now().minusMinutes(30));
    }

    @BeforeEach
    void stubNoTwoFactor() {
        TwoFactorStubs.stubNoTwoFactorForEveryAdmin(WireMockAuthServerResource.server());
    }

    @Test
    @TestSecurity(user = ADMIN_SUB, roles = "admin")
    @JwtSecurity(claims = @Claim(key = "sub", value = ADMIN_SUB))
    @DisplayName("J-C-23: GET status returns the most-recent run per kind (newer 'failed' crawl wins over older 'succeeded')")
    void statusReturnsMostRecentRunPerKind() {
        given().when().get(STATUS)
                .then()
                .statusCode(200)
                .body("crawl.status", equalTo("failed"))
                .body("enrichment.status", equalTo("succeeded"));
    }
}
