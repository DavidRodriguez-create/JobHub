package com.davidcreate.jobhub.job.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Story #430 (ADR 0025 D3, PDA section B): {@code GET /jobs/admin/companies/{id}}.
 */
@QuarkusTest
@TestSecurity(user = "43000000-9996-0000-0000-000000000001", roles = "admin")
@DisplayName("Admin Company Read Component Tests (Story #430)")
class AdminCompanyReadComponentTest {

    private static final String COMPANIES = "/jobs/admin/companies";
    private static final String STRIPE_ID = "c1111111-c111-c111-c111-c11111111111";
    private static final String SPOTIFY_ID = "c2222222-c222-c222-c222-c22222222222";

    // ── QAE-430-R-01 (AC-430-10) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-R-01: admin opens a company with a populated description")
    void openCompanyWithPopulatedDescription() {
        given()
                .when().get(COMPANIES + "/" + STRIPE_ID)
                .then()
                .statusCode(200)
                .body("id", equalTo(STRIPE_ID))
                .body("name", equalTo("Stripe"))
                .body("description", equalTo("Financial infrastructure for the internet."));
    }

    // ── QAE-430-R-02 (AC-430-10) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-R-02: admin opens a company whose description is null - present, explicit null")
    void openCompanyWithNullDescription() {
        given()
                .when().get(COMPANIES + "/" + SPOTIFY_ID)
                .then()
                .statusCode(200)
                .body("name", equalTo("Spotify"))
                .body("$", org.hamcrest.Matchers.hasKey("description"))
                .body("description", nullValue());
    }

    // ── QAE-430-R-03 (AC-430-11) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-R-03: unknown id -> 404 {error, message}, not a null/empty body")
    void unknownIdIsNotFound() {
        given()
                .when().get(COMPANIES + "/" + UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("error", notNullValue())
                .body("message", notNullValue());
    }

    // ── QAE-484-JS-REG-08b ──────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-484-JS-REG-08b (#484 regression): a curated-seed company's core fields "
            + "(slug/industry/size/headquarters/website/logoUrl) are identical across the admin "
            + "detail, admin list, job-search list, and job-detail surfaces, confirming the "
            + "admin manual-edit path remains the single source of truth for company data now "
            + "that automatic enrichment is removed")
    void companyFieldsAreConsistentAcrossEveryReadSurface() {
        JsonPath adminDetail = given()
                .when().get(COMPANIES + "/" + STRIPE_ID)
                .then().statusCode(200).extract().jsonPath();

        // "STRIP" also matches "Striped Media" (QAE-430-B-02) - narrow to the exact name so
        // exactly one list entry is unambiguous.
        Map<String, Object> adminListEntry = given()
                .queryParam("q", "Stripe")
                .when().get(COMPANIES)
                .then().statusCode(200)
                .extract().jsonPath().getMap("find { it.name == 'Stripe' }");

        // Job 1 (target aaaaaaaa -> company c1111111/Stripe, test-seeds.sql).
        JsonPath jobDetail = given()
                .when().get("/jobs/11111111-1111-1111-1111-111111111111")
                .then().statusCode(200).extract().jsonPath();

        // "Quarkus" appears only in job 1's description in the shared fixture, so this
        // keyword search deterministically isolates its content[0] row. JobPostSummary
        // deliberately omits company.description (TC-2 in JobResourceComponentTest), so
        // description is excluded from the field-by-field comparison below - the strongest
        // feasible cross-surface check given that intentional projection gap.
        JsonPath searchResult = given()
                .queryParam("keyword", "Quarkus")
                .when().get("/jobs")
                .then().statusCode(200)
                .body("content.size()", equalTo(1))
                .extract().jsonPath();

        assertThat(adminDetail.getString("name")).isEqualTo("Stripe");
        assertThat(adminListEntry.get("name")).isEqualTo("Stripe");
        assertThat(jobDetail.getString("company.name")).isEqualTo("Stripe");
        assertThat(searchResult.getString("content[0].company.name")).isEqualTo("Stripe");

        for (String field : List.of("slug", "industry", "size", "headquarters", "website", "logoUrl")) {
            Object expected = adminDetail.get(field);
            Object fromAdminList = adminListEntry.get(field);
            Object fromJobDetail = jobDetail.get("company." + field);
            Object fromSearchResult = searchResult.get("content[0].company." + field);
            assertThat(fromAdminList).as("admin list vs admin detail: %s", field).isEqualTo(expected);
            assertThat(fromJobDetail).as("job detail vs admin detail: %s", field).isEqualTo(expected);
            assertThat(fromSearchResult).as("job search vs admin detail: %s", field).isEqualTo(expected);
        }
    }
}
