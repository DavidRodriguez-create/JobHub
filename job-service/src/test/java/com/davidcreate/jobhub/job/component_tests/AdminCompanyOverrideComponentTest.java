package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.AdminCompanyOverrideTestProfile;
import com.davidcreate.jobhub.job.domain.port.in.ResolveCompaniesUseCase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Story #430 (ADR 0025 D2, QAE-430-OV-01/02/04): the record-level override proof. Mirrors
 * {@code CompanyLogoResolutionComponentTest}'s pattern from story #429: its own isolated
 * {@code @TestProfile} container, seeded via raw JDBC in {@code @BeforeAll} on top of the
 * shared baseline, then calls the production {@link ResolveCompaniesUseCase#resolvePending()}
 * directly after an admin PUT edit to prove a fresh crawl changes nothing.
 *
 * <p>QAE-430-OV-03 (no unpin/revert-to-crawl action exists) is a construction-only check:
 * confirmed by inspection of {@code job-service.yaml}'s three {@code Admin} company
 * operations and their generated JAX-RS interfaces, neither of which exposes any parameter
 * or operation that resets {@code manuallyEdited} to {@code false}.
 */
@QuarkusTest
@TestProfile(AdminCompanyOverrideTestProfile.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Admin Company Override Component Tests (Story #430)")
class AdminCompanyOverrideComponentTest {

    private static final String ADMIN_COMPANIES = "/jobs/admin/companies";
    private static final String JOBS = "/jobs";
    private static final String ADMIN_SUB = "43000000-9999-0000-0000-000000000001";

    private static final UUID OVERRIDE_COMPANY_ID = UUID.fromString("43000000-0001-0000-0000-000000000001");
    private static final UUID OVERRIDE_RESOLVED_TARGET_ID = UUID.fromString("43000000-0001-0000-0000-000000000002");
    private static final UUID OVERRIDE_RESOLVED_JOB_ID = UUID.fromString("43000000-0001-0000-0000-000000000003");
    private static final UUID OVERRIDE_UNRESOLVED_TARGET_ID = UUID.fromString("43000000-0001-0000-0000-000000000004");

    private static final UUID CLEARED_COMPANY_ID = UUID.fromString("43000000-0002-0000-0000-000000000001");
    private static final UUID CLEARED_UNRESOLVED_TARGET_ID = UUID.fromString("43000000-0002-0000-0000-000000000002");

    private static final Set<String> EXPECTED_COMPANY_KEYS = Set.of(
            "id", "slug", "name", "website", "industry", "size", "headquarters",
            "description", "tags", "logoUrl", "manuallyEdited", "updatedAt");

    @Inject
    DataSource dataSource;

    @Inject
    ResolveCompaniesUseCase resolveCompaniesUseCase;

    @BeforeAll
    void seedFixture() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            // QAE-430-OV-01: pre-curated headquarters, industry still unset - the admin will
            // edit ONLY industry, then a fresh crawl must leave both fields untouched.
            statement.execute(
                    "INSERT INTO crawler.company "
                            + "(id, slug, name, headquarters, source, manually_edited, created_at, updated_at) "
                            + "VALUES ('" + OVERRIDE_COMPANY_ID + "', 'override-target', 'Override Target', "
                            + "'Old HQ, Country', 'crawl', false, NOW(), NOW())");
            statement.execute(
                    "INSERT INTO crawler.pull_target (id, source_type, company_name, company_id) VALUES "
                            + "('" + OVERRIDE_RESOLVED_TARGET_ID + "', 'greenhouse', 'Override Target', "
                            + "'" + OVERRIDE_COMPANY_ID + "')");
            statement.execute(
                    "INSERT INTO crawler.job_post (id, target_id, title, url, first_seen_at, last_seen_at) "
                            + "VALUES ('" + OVERRIDE_RESOLVED_JOB_ID + "', '" + OVERRIDE_RESOLVED_TARGET_ID + "', "
                            + "'Override Target Engineer', 'https://example.com/jobs/override-target-1', NOW(), NOW())");
            // A brand-new pull target for the SAME employer, still unresolved: this is what
            // resolvePending() resolves in each test below.
            statement.execute(
                    "INSERT INTO crawler.pull_target (id, source_type, company_name) VALUES "
                            + "('" + OVERRIDE_UNRESOLVED_TARGET_ID + "', 'lever', 'Override Target')");

            // QAE-430-OV-02: pre-curated headquarters that will be deliberately CLEARED.
            statement.execute(
                    "INSERT INTO crawler.company "
                            + "(id, slug, name, headquarters, source, manually_edited, created_at, updated_at) "
                            + "VALUES ('" + CLEARED_COMPANY_ID + "', 'cleared-target', 'Cleared Target', "
                            + "'Some City, Country', 'crawl', false, NOW(), NOW())");
            statement.execute(
                    "INSERT INTO crawler.pull_target (id, source_type, company_name) VALUES "
                            + "('" + CLEARED_UNRESOLVED_TARGET_ID + "', 'workday', 'Cleared Target')");
        }
    }

    private static Map<String, Object> echoBody(String industry, String headquarters) {
        Map<String, Object> body = new HashMap<>();
        body.put("industry", industry);
        body.put("headquarters", headquarters);
        return body;
    }

    private int countCompanyRowsForSlug(String slug) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT COUNT(*) FROM crawler.company WHERE slug = '" + slug + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private String companyIdForTarget(UUID targetId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT company_id FROM crawler.pull_target WHERE id = '" + targetId + "'")) {
            assertThat(rs.next()).isTrue();
            return rs.getString("company_id");
        }
    }

    // ── QAE-430-OV-01 (AC-430-22/23) ────────────────────────────────────────────

    @Test
    @TestSecurity(user = ADMIN_SUB, roles = "admin")
    @DisplayName("QAE-430-OV-01: editing ONLY industry pins the WHOLE record - a fresh crawl "
            + "afterward changes neither industry nor the untouched headquarters, no duplicate "
            + "company row, new target assigned the SAME company id")
    void editingOneFieldPinsWholeRecordAgainstFreshCrawl() throws SQLException {
        given()
                .contentType(ContentType.JSON)
                .body(echoBody("Fintech", "Old HQ, Country"))
                .when().put(ADMIN_COMPANIES + "/" + OVERRIDE_COMPANY_ID)
                .then()
                .statusCode(200)
                .body("industry", equalTo("Fintech"));

        resolveCompaniesUseCase.resolvePending();

        given()
                .when().get(ADMIN_COMPANIES + "/" + OVERRIDE_COMPANY_ID)
                .then()
                .statusCode(200)
                .body("industry", equalTo("Fintech"))
                .body("headquarters", equalTo("Old HQ, Country"));

        assertThat(countCompanyRowsForSlug("override-target")).isEqualTo(1);
        assertThat(companyIdForTarget(OVERRIDE_UNRESOLVED_TARGET_ID)).isEqualTo(OVERRIDE_COMPANY_ID.toString());
    }

    // ── QAE-430-OV-02 (AC-430-24) ────────────────────────────────────────────────

    @Test
    @TestSecurity(user = ADMIN_SUB, roles = "admin")
    @DisplayName("QAE-430-OV-02: a deliberately-cleared field also survives a later crawl - "
            + "headquarters stays NULL, never re-filled")
    void deliberatelyClearedFieldSurvivesFreshCrawl() {
        Map<String, Object> clearBody = new HashMap<>();
        clearBody.put("industry", null);
        clearBody.put("headquarters", null);

        given()
                .contentType(ContentType.JSON)
                .body(clearBody)
                .when().put(ADMIN_COMPANIES + "/" + CLEARED_COMPANY_ID)
                .then()
                .statusCode(200)
                .body("headquarters", nullValue());

        resolveCompaniesUseCase.resolvePending();

        given()
                .when().get(ADMIN_COMPANIES + "/" + CLEARED_COMPANY_ID)
                .then()
                .statusCode(200)
                .body("headquarters", nullValue());
    }

    // ── QAE-430-OV-04 (AC-430-26) ────────────────────────────────────────────────

    @Test
    @TestSecurity(user = ADMIN_SUB, roles = "admin")
    @DisplayName("QAE-430-OV-04: manuallyEdited is the ONLY externally-visible provenance signal, "
            + "across all four shapes that can carry the curated company")
    void manuallyEditedIsOnlyProvenanceSignalAcrossAllShapes() {
        given()
                .contentType(ContentType.JSON)
                .body(echoBody("Fintech", "Old HQ, Country"))
                .when().put(ADMIN_COMPANIES + "/" + OVERRIDE_COMPANY_ID)
                .then().statusCode(200);

        given()
                .when().get(ADMIN_COMPANIES + "/" + OVERRIDE_COMPANY_ID)
                .then()
                .statusCode(200)
                .body("manuallyEdited", equalTo(true))
                .body("keySet()", equalTo(EXPECTED_COMPANY_KEYS));

        given()
                .queryParam("q", "Override Target")
                .when().get(ADMIN_COMPANIES)
                .then()
                .statusCode(200)
                .body("find { it.slug == 'override-target' }.manuallyEdited", equalTo(true))
                .body("find { it.slug == 'override-target' }.keySet()", equalTo(EXPECTED_COMPANY_KEYS));

        given()
                .when().get(JOBS + "/" + OVERRIDE_RESOLVED_JOB_ID)
                .then()
                .statusCode(200)
                .body("company.manuallyEdited", equalTo(true))
                .body("company.keySet()", equalTo(EXPECTED_COMPANY_KEYS));

        given()
                .queryParam("keyword", "Override Target Engineer")
                .when().get(JOBS)
                .then()
                .statusCode(200)
                .body("content.find { it.id == '" + OVERRIDE_RESOLVED_JOB_ID + "' }.company.manuallyEdited",
                        equalTo(true))
                .body("content.find { it.id == '" + OVERRIDE_RESOLVED_JOB_ID + "' }.company.keySet()",
                        equalTo(EXPECTED_COMPANY_KEYS));
    }
}
