package com.davidcreate.jobhub.job.component_tests;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story #430 (ADR 0025 D3, PDA sections B/D): 401 anonymous / 403 non-admin, on all three
 * admin company endpoints. Uses the existing "Stripe" seed id (no seed changes needed): none
 * of these cases ever reach a write. "Nothing was written" is verified directly against the
 * database (not via a re-authenticated follow-up GET, since {@code @TestSecurity} is fixed
 * for the whole test method) against Stripe's known seeded values.
 */
@QuarkusTest
@DisplayName("Admin Company Authorization Component Tests (Story #430)")
class AdminCompanyAuthorizationComponentTest {

    private static final String COMPANIES = "/jobs/admin/companies";
    private static final String STRIPE_ID = "c1111111-c111-c111-c111-c11111111111";

    @Inject
    DataSource dataSource;

    private static Map<String, Object> wellFormedUpdateBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("website", "https://stripe.com");
        body.put("industry", "Fintech");
        body.put("size", "5001-10000");
        body.put("headquarters", "San Francisco, United States");
        body.put("description", "Financial infrastructure for the internet.");
        body.put("tags", List.of("fintech", "payments"));
        body.put("logoUrl", "https://example.com/logos/stripe.png");
        return body;
    }

    private void assertStripeUnchangedInDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT website, industry, manually_edited FROM crawler.company "
                                + "WHERE id = '" + STRIPE_ID + "'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("website")).isEqualTo("https://stripe.com");
            assertThat(rs.getString("industry")).isEqualTo("Fintech");
            assertThat(rs.getBoolean("manually_edited")).isFalse();
        }
    }

    // ── QAE-430-AUTHZ-01/02: browse ────────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-AUTHZ-01 (AC-430-01): anonymous browse -> 401, no company data")
    void anonymousBrowseIsUnauthorized() {
        given().when().get(COMPANIES).then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "43000000-9998-0000-0000-000000000001", roles = "user")
    @DisplayName("QAE-430-AUTHZ-02 (AC-430-02): authenticated non-admin browse -> 403")
    void nonAdminBrowseIsForbidden() {
        given().when().get(COMPANIES).then().statusCode(403);
    }

    // ── QAE-430-AUTHZ-03/04: read-one ──────────────────────────────────────────

    @Test
    @DisplayName("QAE-430-AUTHZ-03 (AC-430-12): anonymous read-one -> 401")
    void anonymousReadOneIsUnauthorized() {
        given().when().get(COMPANIES + "/" + STRIPE_ID).then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "43000000-9998-0000-0000-000000000002", roles = "user")
    @DisplayName("QAE-430-AUTHZ-04 (AC-430-12): authenticated non-admin read-one -> 403")
    void nonAdminReadOneIsForbidden() {
        given().when().get(COMPANIES + "/" + STRIPE_ID).then().statusCode(403);
    }

    // ── QAE-430-AUTHZ-05/06: update, and nothing is written ────────────────────

    @Test
    @DisplayName("QAE-430-AUTHZ-05 (AC-430-19): anonymous update -> 401, row left unchanged")
    void anonymousUpdateIsUnauthorizedAndWritesNothing() throws SQLException {
        given()
                .contentType(ContentType.JSON)
                .body(wellFormedUpdateBody())
                .when().put(COMPANIES + "/" + STRIPE_ID)
                .then().statusCode(401);

        assertStripeUnchangedInDatabase();
    }

    @Test
    @TestSecurity(user = "43000000-9998-0000-0000-000000000003", roles = "user")
    @DisplayName("QAE-430-AUTHZ-06 (AC-430-20): authenticated non-admin update -> 403, row left unchanged")
    void nonAdminUpdateIsForbiddenAndWritesNothing() throws SQLException {
        given()
                .contentType(ContentType.JSON)
                .body(wellFormedUpdateBody())
                .when().put(COMPANIES + "/" + STRIPE_ID)
                .then().statusCode(403);

        assertStripeUnchangedInDatabase();
    }
}
