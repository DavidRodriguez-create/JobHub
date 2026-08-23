package com.davidcreate.jobhub.job.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Story #428 (ADR 0023): company-specific scenarios not already covered by the extended
 * {@link JobResourceComponentTest} assertions - merge, name-only, the transitional
 * NULL-{@code company_id} fallback, null-logo-but-resolved, the decoy-logo regression lock,
 * and the facet-still-splits known/accepted behaviour.
 */
@QuarkusTest
@DisplayName("Company Resolution Component Tests (Story #428)")
class CompanyResolutionComponentTest {

    private static final String JOBS = "/jobs";
    private static final String FACETS = "/jobs/facets";

    // Row 12 (Nestlé S.A., greenhouse) and row 13 (NESTLE SA, lever), both pre-linked to
    // the same seeded crawler.company row (slug "nestle").
    private static final UUID NESTLE_GREENHOUSE_JOB_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID NESTLE_LEVER_JOB_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000013");

    // Row 14 (Acme Only, workday), pre-linked to a company row where only name is set.
    private static final UUID ACME_ONLY_JOB_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000014");

    // Row 11 (Northwind Freight, workday), whose pull target's company_id stays NULL.
    private static final UUID NORTHWIND_FREIGHT_JOB_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000011");

    // Any Spotify posting: the Spotify company row has industry/size set but logo_url NULL.
    private static final UUID SPOTIFY_JOB_ID =
            UUID.fromString("44444444-4444-4444-4444-444444444444");

    // ── QAE-428-C-04 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-428-C-04 (AC-428-03/04/06): same employer, two sources, merges to one "
            + "company row - both postings return the identical company.id/slug/name")
    void sameEmployerTwoSourcesMergeToOneCompanyRow() {
        var greenhouse = given()
                .pathParam("id", NESTLE_GREENHOUSE_JOB_ID)
                .when().get(JOBS + "/{id}")
                .then().statusCode(200)
                .extract().jsonPath();

        var lever = given()
                .pathParam("id", NESTLE_LEVER_JOB_ID)
                .when().get(JOBS + "/{id}")
                .then().statusCode(200)
                .extract().jsonPath();

        assertThat(greenhouse.getString("company.slug")).isEqualTo("nestle");
        assertThat(lever.getString("company.slug")).isEqualTo("nestle");
        assertThat(greenhouse.getString("company.id")).isEqualTo(lever.getString("company.id"));
        assertThat(greenhouse.getString("company.name")).isEqualTo(lever.getString("company.name"));
    }

    // ── QAE-428-C-05 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-428-C-05 (AC-428-09/11/29): name-only company - id/slug/manuallyEdited "
            + "known (resolved), every other field null, never \"\" or \"-\"")
    void nameOnlyCompanyReturnsNulls() {
        given()
                .pathParam("id", ACME_ONLY_JOB_ID)
                .when().get(JOBS + "/{id}")
                .then()
                .statusCode(200)
                .body("company.id", notNullValue())
                .body("company.slug", equalTo("acme-only"))
                .body("company.name", equalTo("Acme Only"))
                .body("company.manuallyEdited", equalTo(false))
                .body("company.website", nullValue())
                .body("company.industry", nullValue())
                .body("company.size", nullValue())
                .body("company.headquarters", nullValue())
                .body("company.description", nullValue())
                .body("company.logoUrl", nullValue())
                .body("company.tags", nullValue());
    }

    // ── QAE-428-C-06 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-428-C-06 (AC-428-13/30): transitional NULL company_id fallback - "
            + "company.name from pull_target.company_name, every other field null")
    void transitionalNullCompanyIdFallback() {
        given()
                .pathParam("id", NORTHWIND_FREIGHT_JOB_ID)
                .when().get(JOBS + "/{id}")
                .then()
                .statusCode(200)
                .body("company.name", equalTo("Northwind Freight"))
                .body("company.id", nullValue())
                .body("company.slug", nullValue())
                .body("company.website", nullValue())
                .body("company.industry", nullValue())
                .body("company.size", nullValue())
                .body("company.headquarters", nullValue())
                .body("company.description", nullValue())
                .body("company.logoUrl", nullValue())
                .body("company.tags", nullValue())
                .body("company.manuallyEdited", nullValue())
                .body("company.updatedAt", nullValue());
    }

    // ── QAE-428-C-07 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-428-C-07 (AC-428-02/09/29): null logo, otherwise resolved - "
            + "industry non-null (distinct from the fully-sparse C-05 case), logoUrl null")
    void nullLogoOtherwiseResolved() {
        given()
                .pathParam("id", SPOTIFY_JOB_ID)
                .when().get(JOBS + "/{id}")
                .then()
                .statusCode(200)
                .body("company.slug", equalTo("spotify"))
                .body("company.industry", equalTo("Music Streaming"))
                .body("company.logoUrl", nullValue());
    }

    // ── QAE-428-C-08 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-428-C-08: decoy pull_target.company_logo_url is fully ignored once "
            + "resolved - response carries the REAL crawler.company.logo_url, never the decoy")
    void decoyPullTargetLogoIgnoredOnceResolved() {
        given()
                .pathParam("id", NESTLE_GREENHOUSE_JOB_ID)
                .when().get(JOBS + "/{id}")
                .then()
                .statusCode(200)
                .body("company.logoUrl", equalTo("https://example.com/logos/nestle.png"))
                .body("company.logoUrl", org.hamcrest.Matchers.not(
                        equalTo("https://example.com/logos/DECOY-should-not-appear.png")));
    }

    // ── QAE-428-C-09 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-428-C-09 (AC-428-27/28, known/accepted, NOT a bug): the merged Nestlé "
            + "pair still shows as TWO separate facet buckets, grouped by raw company_name")
    void facetStillSplitsMergedPair() {
        given()
                .when().get(FACETS)
                .then()
                .statusCode(200)
                .body("companies.find { it.value == 'Nestlé S.A.' }.count", equalTo(1))
                .body("companies.find { it.value == 'NESTLE SA' }.count", equalTo(1));
    }
}
