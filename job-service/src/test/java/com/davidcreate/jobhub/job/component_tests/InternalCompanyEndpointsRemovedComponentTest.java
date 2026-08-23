package com.davidcreate.jobhub.job.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * Story #484 (QAE-484-JS-GONE-01): the {@code /internal/companies/*} routes no longer exist
 * at all now that {@link com.davidcreate.jobhub.job.adapter.in.rest.InternalCompanyResource}
 * and {@code ServiceKeyFilter} are removed, so a request against either path must fail
 * because the route itself is unmatched (404), never because the pre-shared {@code
 * X-Service-Key} header check fails (401) - that filter no longer exists to reject anything.
 */
@QuarkusTest
@DisplayName("Internal Company Endpoints Removed Component Test (#484)")
class InternalCompanyEndpointsRemovedComponentTest {

    // ── QAE-484-JS-GONE-01 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-484-JS-GONE-01: GET /internal/companies/pending-enrichment -> 404, not 401 - "
            + "no X-Service-Key header supplied, proving the route is gone rather than guarded")
    void pendingEnrichmentRouteIsGone() {
        given()
                .when().get("/internal/companies/pending-enrichment")
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("QAE-484-JS-GONE-01: POST /internal/companies/{id}/enrichment -> 404, not 401 - "
            + "no X-Service-Key header supplied, proving the route is gone rather than guarded")
    void applyEnrichmentRouteIsGone() {
        given()
                .contentType("application/json")
                .body("{}")
                .when().post("/internal/companies/" + UUID.randomUUID() + "/enrichment")
                .then()
                .statusCode(404);
    }
}
