package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.CompanyLogoResolutionTestProfile;
import com.davidcreate.jobhub.job.domain.port.in.ResolveCompaniesUseCase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
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
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Story #429 (ADR 0024, QAE-429-C-01..05): exercises the production
 * {@link ResolveCompaniesUseCase#resolvePending()} directly (a real write against the real
 * schema, unlike {@link CompanyResolutionComponentTest} which only reads rows the seed
 * script already resolved), against its own isolated container
 * ({@link CompanyLogoResolutionTestProfile}) seeded with the QAE's own fixture on top of the
 * baseline {@code test-seeds.sql}.
 */
@QuarkusTest
@TestProfile(CompanyLogoResolutionTestProfile.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Company Logo Resolution Component Tests (Story #429)")
class CompanyLogoResolutionComponentTest {

    private static final String JOBS = "/jobs";

    private static final UUID GLOBEX_TARGET_ID = UUID.fromString("42900001-0000-0000-0000-000000000001");
    private static final UUID GLOBEX_JOB_ID = UUID.fromString("42900001-0000-0000-0000-000000000002");
    private static final UUID DELIVERY_HERO_TARGET_ID = UUID.fromString("42900002-0000-0000-0000-000000000001");
    private static final UUID DELIVERY_HERO_JOB_ID = UUID.fromString("42900002-0000-0000-0000-000000000002");
    private static final UUID STRIPE_INC_TARGET_ID = UUID.fromString("42900003-0000-0000-0000-000000000001");
    private static final UUID MANUAL_CLEARED_COMPANY_ID = UUID.fromString("42900004-0000-0000-0000-000000000000");
    private static final UUID MANUAL_CLEARED_TARGET_ID = UUID.fromString("42900004-0000-0000-0000-000000000001");

    // Baseline fixture (test-seeds.sql), pre-resolved by Story #428's own seed data.
    private static final UUID STRIPE_JOB_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NESTLE_GREENHOUSE_JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");

    @Inject
    DataSource dataSource;

    @Inject
    ResolveCompaniesUseCase resolveCompaniesUseCase;

    @BeforeAll
    void seedFixtureAndResolve() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            // QAE-429-C-01: brand-new company, simple slug.
            statement.execute(
                    "INSERT INTO crawler.pull_target (id, source_type, company_name) VALUES "
                            + "('" + GLOBEX_TARGET_ID + "', 'greenhouse', 'Globex Corp')");
            statement.execute(
                    "INSERT INTO crawler.job_post (id, target_id, title, url, first_seen_at, last_seen_at) "
                            + "VALUES ('" + GLOBEX_JOB_ID + "', '" + GLOBEX_TARGET_ID + "', 'Backend Engineer', "
                            + "'https://example.com/jobs/globex-1', NOW(), NOW())");

            // QAE-429-C-02: brand-new company, hyphenated slug.
            statement.execute(
                    "INSERT INTO crawler.pull_target (id, source_type, company_name) VALUES "
                            + "('" + DELIVERY_HERO_TARGET_ID + "', 'lever', 'Delivery Hero')");
            statement.execute(
                    "INSERT INTO crawler.job_post (id, target_id, title, url, first_seen_at, last_seen_at) "
                            + "VALUES ('" + DELIVERY_HERO_JOB_ID + "', '" + DELIVERY_HERO_TARGET_ID + "', "
                            + "'Logistics Analyst', 'https://example.com/jobs/deliveryhero-1', NOW(), NOW())");

            // QAE-429-C-03: collides with the already-seeded 'stripe' company row - lookup-hit
            // only, no job_post needed.
            statement.execute(
                    "INSERT INTO crawler.pull_target (id, source_type, company_name) VALUES "
                            + "('" + STRIPE_INC_TARGET_ID + "', 'workday', 'Stripe Inc')");

            // QAE-429-C-04: a manually-edited company with an intentionally-cleared logo, plus
            // its own unresolved pull target.
            statement.execute(
                    "INSERT INTO crawler.company "
                            + "(id, slug, name, logo_url, source, manually_edited, created_at, updated_at) "
                            + "VALUES ('" + MANUAL_CLEARED_COMPANY_ID + "', 'manual-cleared-co', "
                            + "'Manual Cleared Co', NULL, 'manual', true, NOW(), NOW())");
            statement.execute(
                    "INSERT INTO crawler.pull_target (id, source_type, company_name) VALUES "
                            + "('" + MANUAL_CLEARED_TARGET_ID + "', 'greenhouse', 'Manual Cleared Co')");
        }

        resolveCompaniesUseCase.resolvePending();
    }

    private String logoUrlForSlug(String slug) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT logo_url FROM crawler.company WHERE slug = '" + slug + "'")) {
            assertThat(rs.next()).as("company row for slug '" + slug + "' must exist").isTrue();
            return rs.getString("logo_url");
        }
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

    // ── QAE-429-C-01 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-429-C-01 (AC-429-01): brand-new company, first resolution - new "
            + "crawler.company row for slug 'globex' is born with a NULL logo_url (logos are "
            + "curated/admin-filled, never derived), AND GET /jobs/{id} exposes company.logoUrl as null")
    void brandNewCompanyBornWithNullLogo() throws SQLException {
        assertThat(countCompanyRowsForSlug("globex")).isEqualTo(1);
        assertThat(logoUrlForSlug("globex")).isNull();

        given()
                .pathParam("id", GLOBEX_JOB_ID)
                .when().get(JOBS + "/{id}")
                .then()
                .statusCode(200)
                .body("company.logoUrl", nullValue());
    }

    // ── QAE-429-C-02 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-429-C-02 (AC-429-02): brand-new company, hyphenated slug - also born with "
            + "a NULL logo_url, AND GET /jobs/{id} exposes company.logoUrl as null")
    void brandNewCompanyHyphenatedSlugBornWithNullLogo() throws SQLException {
        assertThat(logoUrlForSlug("delivery-hero")).isNull();

        given()
                .pathParam("id", DELIVERY_HERO_JOB_ID)
                .when().get(JOBS + "/{id}")
                .then()
                .statusCode(200)
                .body("company.logoUrl", nullValue());
    }

    // ── QAE-429-C-03 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-429-C-03 (AC-429-05/10): lookup-hit never overwrites an existing logo - "
            + "no duplicate 'stripe' row, the seeded logo_url survives, new target assigned "
            + "the SAME existing company id")
    void lookupHitNeverOverwritesExistingLogo() throws SQLException {
        assertThat(countCompanyRowsForSlug("stripe")).isEqualTo(1);
        assertThat(logoUrlForSlug("stripe")).isEqualTo("https://example.com/logos/stripe.png");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT pt.company_id, c.id AS existing_id FROM crawler.pull_target pt "
                                + "JOIN crawler.company c ON c.slug = 'stripe' "
                                + "WHERE pt.id = '" + STRIPE_INC_TARGET_ID + "'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("company_id")).isEqualTo(rs.getString("existing_id"));
        }
    }

    // ── QAE-429-C-04 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-429-C-04 (AC-429-10): manually-edited, intentionally-null logo survives "
            + "a resolve cycle - logo_url stays NULL, manually_edited stays TRUE")
    void manuallyEditedClearedLogoSurvivesResolveCycle() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT logo_url, manually_edited FROM crawler.company "
                                + "WHERE slug = 'manual-cleared-co'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("logo_url")).isNull();
            assertThat(rs.getBoolean("manually_edited")).isTrue();
        }
    }

    // ── QAE-429-C-05 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-429-C-05 (AC-429-16, regression): read path is unchanged for rows #428 "
            + "already resolved - pre-existing Stripe/Nestle postings still return their own logoUrl")
    void readPathUnchangedForPreExistingResolvedRows() {
        given()
                .pathParam("id", STRIPE_JOB_ID)
                .when().get(JOBS + "/{id}")
                .then()
                .statusCode(200)
                .body("company.logoUrl", equalTo("https://example.com/logos/stripe.png"));

        given()
                .pathParam("id", NESTLE_GREENHOUSE_JOB_ID)
                .when().get(JOBS + "/{id}")
                .then()
                .statusCode(200)
                .body("company.logoUrl", equalTo("https://example.com/logos/nestle.png"));
    }
}
