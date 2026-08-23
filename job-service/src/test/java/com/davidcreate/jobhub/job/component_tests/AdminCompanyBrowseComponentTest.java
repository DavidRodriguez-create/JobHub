package com.davidcreate.jobhub.job.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;

/**
 * Story #430 (ADR 0025 D3, PDA section A): {@code GET /jobs/admin/companies}. The shared
 * baseline seed fixture carries 13 {@code crawler.company} rows (four from #428/#429 -
 * Stripe/Spotify/Nestle/Acme Only - plus nine dedicated #430 rows, section 0 of the QAE doc):
 * N = 13 for every unfiltered total-count assertion below.
 */
@QuarkusTest
@TestSecurity(user = "43000000-9997-0000-0000-000000000001", roles = "admin")
@DisplayName("Admin Company Browse Component Tests (Story #430)")
class AdminCompanyBrowseComponentTest {

    private static final String COMPANIES = "/jobs/admin/companies";
    private static final int TOTAL_COMPANIES = 13;

    // ── QAE-430-B-01 (AC-430-03) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-B-01: default sort/pagination - alphabetical by name ascending, "
            + "at most 20 entries, X-Total-Count == N")
    void defaultSortAndPagination() {
        given()
                .when().get(COMPANIES)
                .then()
                .statusCode(200)
                .header("X-Total-Count", equalTo(String.valueOf(TOTAL_COMPANIES)))
                .body("size()", lessThanOrEqualTo(20))
                .body("name", hasSize(TOTAL_COMPANIES));

        // "Acme Only" before "Stripe" before "Striped Media", alphabetically.
        var names = given().when().get(COMPANIES).then().extract().jsonPath().getList("name", String.class);
        int acmeIdx = names.indexOf("Acme Only");
        int stripeIdx = names.indexOf("Stripe");
        int stripedIdx = names.indexOf("Striped Media");
        org.assertj.core.api.Assertions.assertThat(acmeIdx).isLessThan(stripeIdx);
        org.assertj.core.api.Assertions.assertThat(stripeIdx).isLessThan(stripedIdx);
    }

    // ── QAE-430-B-02 (AC-430-04) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-B-02: q filters by a case-insensitive name substring")
    void qFiltersCaseInsensitiveSubstring() {
        given()
                .queryParam("q", "STRIP")
                .when().get(COMPANIES)
                .then()
                .statusCode(200)
                .header("X-Total-Count", equalTo("2"))
                .body("name", containsInAnyOrder("Stripe", "Striped Media"));
    }

    // ── QAE-430-B-03 (AC-430-05) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-B-03: manuallyEdited=false surfaces the enrichment backlog")
    void manuallyEditedFalseSurfacesBacklog() {
        given()
                .queryParam("manuallyEdited", false)
                .queryParam("size", 100)
                .when().get(COMPANIES)
                .then()
                .statusCode(200)
                .body("manuallyEdited", everyItem(equalTo(false)))
                .body("name", not(org.hamcrest.Matchers.hasItem("Curated Alpha Co")));
    }

    // ── QAE-430-B-04 (AC-430-06) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-B-04: manuallyEdited=true surfaces already-curated companies")
    void manuallyEditedTrueSurfacesCurated() {
        given()
                .queryParam("manuallyEdited", true)
                .queryParam("size", 100)
                .when().get(COMPANIES)
                .then()
                .statusCode(200)
                .body("manuallyEdited", everyItem(equalTo(true)))
                .body("name", org.hamcrest.Matchers.hasItem("Curated Alpha Co"))
                .body("name", not(org.hamcrest.Matchers.hasItem("Stripe")));
    }

    // ── QAE-430-B-05 (AC-430-07) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-B-05: browse returns the FULL projection, description included")
    void browseReturnsFullProjectionWithDescription() {
        given()
                .queryParam("q", "Stripe")
                .when().get(COMPANIES)
                .then()
                .statusCode(200)
                .body("find { it.name == 'Stripe' }.description",
                        equalTo("Financial infrastructure for the internet."));
    }

    // ── QAE-430-B-06 (AC-430-08) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-B-06: X-Total-Count is independent of the requested page size")
    void totalCountIndependentOfPageSize() {
        given()
                .queryParam("page", 0)
                .queryParam("size", 2)
                .when().get(COMPANIES)
                .then()
                .statusCode(200)
                .header("X-Total-Count", equalTo(String.valueOf(TOTAL_COMPANIES)))
                .body("size()", lessThanOrEqualTo(2));
    }

    // ── QAE-430-B-07 (AC-430-09) ────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-B-07: no matches is an empty list, not an error")
    void noMatchesIsEmptyList() {
        given()
                .queryParam("q", "zzz-no-such-company-exists")
                .when().get(COMPANIES)
                .then()
                .statusCode(200)
                .header("X-Total-Count", equalTo("0"))
                .body("$", empty());
    }

    // ── QAE-430-B-08 (contract-completeness) ────────────────────────────────────

    @Test
    @DisplayName("QAE-430-B-08: every sort enum value produces a distinguishable order")
    void everySortEnumValueOrders() {
        var descNames = given().queryParam("sort", "name-desc").queryParam("size", 100)
                .when().get(COMPANIES).then().statusCode(200)
                .extract().jsonPath().getList("name", String.class);
        int stripedIdx = descNames.indexOf("Striped Media");
        int acmeIdx = descNames.indexOf("Acme Only");
        org.assertj.core.api.Assertions.assertThat(stripedIdx).isLessThan(acmeIdx);

        var updatedDesc = given().queryParam("sort", "updated-desc").queryParam("size", 100)
                .when().get(COMPANIES).then().statusCode(200)
                .extract().jsonPath().getList("name", String.class);
        int curatedIdx = updatedDesc.indexOf("Curated Alpha Co");
        int stripeIdx = updatedDesc.indexOf("Stripe");
        org.assertj.core.api.Assertions.assertThat(curatedIdx).isLessThan(stripeIdx);

        var updatedAsc = given().queryParam("sort", "updated-asc").queryParam("size", 100)
                .when().get(COMPANIES).then().statusCode(200)
                .extract().jsonPath().getList("name", String.class);
        int stripeIdxAsc = updatedAsc.indexOf("Stripe");
        int curatedIdxAsc = updatedAsc.indexOf("Curated Alpha Co");
        org.assertj.core.api.Assertions.assertThat(stripeIdxAsc).isLessThan(curatedIdxAsc);
    }

    // ── QAE-430-B-09 (contract-completeness, 400) ───────────────────────────────

    @Test
    @DisplayName("QAE-430-B-09: size outside [1,100] is rejected")
    void sizeOutsideBoundsIsRejected() {
        given().queryParam("size", 0).when().get(COMPANIES)
                .then().statusCode(400).body("error", org.hamcrest.Matchers.notNullValue());

        given().queryParam("size", 101).when().get(COMPANIES)
                .then().statusCode(400).body("error", org.hamcrest.Matchers.notNullValue());
    }

    // ── QAE-430-B-10 (contract-completeness, 400) ───────────────────────────────

    @Test
    @DisplayName("QAE-430-B-10: page below 0 is rejected")
    void negativePageIsRejected() {
        given().queryParam("page", -1).when().get(COMPANIES)
                .then().statusCode(400).body("error", org.hamcrest.Matchers.notNullValue());
    }
}
