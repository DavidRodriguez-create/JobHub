package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.MigrationTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story #428 (QAE-428-MIG-01/02): executes the LITERAL {@code db/init/051-job-company.sql}
 * file (byte-for-byte, not a paraphrase) against a schema that mirrors PRE-051 prod, via
 * {@link MigrationTestProfile}'s dedicated init script and Hibernate schema management
 * turned off. This is a distinct mechanism from every other component test in this
 * service, which relies on Hibernate's entity-driven drop-and-create - that path never
 * executes anything under {@code db/init/} at all.
 */
@QuarkusTest
@TestProfile(MigrationTestProfile.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Company Migration Component Test (db/init/051-job-company.sql, Story #428)")
class CompanyMigrationComponentTest {

    private static final Path MIGRATION_FILE = Path.of("../db/init/051-job-company.sql");

    @Inject
    DataSource dataSource;

    private boolean migrationApplied = false;

    @BeforeEach
    void applyMigrationOnce() throws IOException, SQLException {
        if (migrationApplied) {
            return;
        }
        String sql = Files.readString(MIGRATION_FILE);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
        migrationApplied = true;
    }

    // ── QAE-428-MIG-01 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-428-MIG-01 (AC-428-12): every pull_target row ends with a non-null "
            + "company_id; the two Nestlé-spelling rows collapse into exactly ONE company row")
    void everyPullTargetResolvesAndTheNestlePairCollapsesToOneRow() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            try (ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM crawler.pull_target WHERE company_id IS NULL")) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("every pull_target row must resolve a non-null company_id")
                        .isZero();
            }

            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM crawler.company")) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("distinct slugs: acme, nestle, northwind-freight -> 3 company rows, "
                                + "the Nestlé pair must NOT create two")
                        .isEqualTo(3);
            }
        }
    }

    @Test
    @DisplayName("QAE-428-MIG-01: the null-logo pull target's resulting company.logo_url is "
            + "still NULL - never '', never a crash")
    void nullLogoRowResultingCompanyLogoUrlStaysNull() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT c.logo_url FROM crawler.pull_target pt "
                                + "JOIN crawler.company c ON c.id = pt.company_id "
                                + "WHERE pt.company_name = 'Northwind Freight'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("logo_url")).isNull();
        }
    }

    @Test
    @DisplayName("QAE-428-MIG-01: the newly-created nestle company row satisfies "
            + "chk_company_slug_format and is unique (uq_company_slug holds)")
    void nestleCompanyRowSatisfiesSlugFormatAndUniqueness() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT slug FROM crawler.company WHERE slug = 'nestle'")) {
            assertThat(rs.next())
                    .as("a company row with slug='nestle' must exist")
                    .isTrue();
            String slug = rs.getString("slug");
            assertThat(slug).matches("^[a-z0-9]+(-[a-z0-9]+)*$");
            assertThat(rs.next())
                    .as("uq_company_slug must hold: exactly one 'nestle' row")
                    .isFalse();
        }
    }

    // ── QAE-428-MIG-02 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-428-MIG-02 (AC-428-12): the migration file's own closing verification "
            + "SELECT reports the two counts equal")
    void ownVerificationSelectReportsCountsEqual() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT (SELECT COUNT(*) FROM crawler.pull_target) AS total_pull_targets,"
                                + " (SELECT COUNT(*) FROM crawler.pull_target WHERE company_id IS NOT NULL)"
                                + " AS resolved_pull_targets")) {
            assertThat(rs.next()).isTrue();
            int total = rs.getInt("total_pull_targets");
            int resolved = rs.getInt("resolved_pull_targets");
            assertThat(resolved)
                    .as("the migration's own verification SELECT must show every pull target resolved")
                    .isEqualTo(total);
            assertThat(total).isEqualTo(4);
        }
    }
}
