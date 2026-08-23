package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.CompanyEnrichmentDropMigrationTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
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
 * Story #484 (QAE-484-JS-MIG-01, load-bearing): executes the LITERAL {@code
 * db/init/057-company-drop-enrichment-tracking.sql} file (byte-for-byte, not a paraphrase)
 * against a schema that has already had {@code db/init/051-job-company.sql} then {@code
 * db/init/053-company-enrichment-tracking.sql} applied (so the columns/index exist first,
 * exactly as prod history did), mirroring {@code CompanyEnrichmentMigrationComponentTest}'s
 * now-removed mechanism and {@link CompanyLogoBackfillMigrationComponentTest}'s pattern.
 *
 * <p>This is the only proof that 057 actually drops the tracking columns/index: this test
 * profile uses {@code drop-and-create} against the CURRENT {@code CompanyEntity} (which
 * already has no {@code enrichedAt}/{@code enrichmentAttempts} fields), so Hibernate {@code
 * validate} never sees the pre-057 shape and cannot catch a 057 that silently no-ops.
 */
@QuarkusTest
@TestProfile(CompanyEnrichmentDropMigrationTestProfile.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Company Enrichment Drop Migration Component Test (db/init/057, Story #484)")
class CompanyEnrichmentDropMigrationComponentTest {

    private static final Path MIGRATION_051 = Path.of("../db/init/051-job-company.sql");
    private static final Path MIGRATION_053 = Path.of("../db/init/053-company-enrichment-tracking.sql");
    private static final Path MIGRATION_057 = Path.of("../db/init/057-company-drop-enrichment-tracking.sql");

    @Inject
    DataSource dataSource;

    @BeforeAll
    void applyPrerequisitesSeedFixtureAndRunMigrationOnce() throws IOException, SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(MIGRATION_051));
            statement.execute(Files.readString(MIGRATION_053));

            // Seeded AFTER 053 (so the pre-057 row shape has real enriched_at/enrichment_attempts
            // values), BEFORE 057, so QAE-484-JS-MIG-01's data-preservation assertion has a
            // non-default value to prove survives the column drop untouched.
            statement.execute(
                    "INSERT INTO crawler.company (slug, name, source, manually_edited, industry) "
                            + "VALUES ('pre-057-alpha', 'Pre 057 Alpha', 'crawl', false, 'Fintech')");
            statement.execute(
                    "UPDATE crawler.company SET enriched_at = now(), enrichment_attempts = 3 "
                            + "WHERE slug = 'pre-057-alpha'");

            statement.execute(Files.readString(MIGRATION_057));
        }
    }

    // ── QAE-484-JS-MIG-01 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-484-JS-MIG-01: enriched_at no longer exists on crawler.company")
    void enrichedAtColumnIsGone() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT 1 FROM information_schema.columns "
                                + "WHERE table_schema = 'crawler' AND table_name = 'company' "
                                + "AND column_name = 'enriched_at'")) {
            assertThat(rs.next()).as("enriched_at column must no longer exist").isFalse();
        }
    }

    @Test
    @DisplayName("QAE-484-JS-MIG-01: enrichment_attempts no longer exists on crawler.company")
    void enrichmentAttemptsColumnIsGone() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT 1 FROM information_schema.columns "
                                + "WHERE table_schema = 'crawler' AND table_name = 'company' "
                                + "AND column_name = 'enrichment_attempts'")) {
            assertThat(rs.next()).as("enrichment_attempts column must no longer exist").isFalse();
        }
    }

    @Test
    @DisplayName("QAE-484-JS-MIG-01: idx_company_enrich_pending no longer exists")
    void enrichPendingIndexIsGone() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT 1 FROM pg_indexes WHERE schemaname = 'crawler' "
                                + "AND tablename = 'company' AND indexname = 'idx_company_enrich_pending'")) {
            assertThat(rs.next()).as("idx_company_enrich_pending must no longer exist").isFalse();
        }
    }

    @Test
    @DisplayName("QAE-484-JS-MIG-01: dropping the tracking columns leaves every other column's "
            + "data on the row untouched - the drop is scoped to exactly the two named columns")
    void otherColumnDataSurvivesTheDrop() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT name, source, manually_edited, industry FROM crawler.company "
                                + "WHERE slug = 'pre-057-alpha'")) {
            assertThat(rs.next()).as("pre-057-alpha row must still exist").isTrue();
            assertThat(rs.getString("name")).isEqualTo("Pre 057 Alpha");
            assertThat(rs.getString("source")).isEqualTo("crawl");
            assertThat(rs.getBoolean("manually_edited")).isFalse();
            assertThat(rs.getString("industry")).isEqualTo("Fintech");
        }
    }
}
