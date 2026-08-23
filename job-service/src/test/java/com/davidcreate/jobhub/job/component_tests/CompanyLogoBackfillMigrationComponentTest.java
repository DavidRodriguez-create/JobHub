package com.davidcreate.jobhub.job.component_tests;

import com.davidcreate.jobhub.job.component_tests.support.CompanyLogoBackfillMigrationTestProfile;
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
 * Story #429 (revised ADR 0024, #451, QAE-429-MIG-01..06): executes the LITERAL
 * {@code db/init/052-company-logo-backfill.sql} file (byte-for-byte, not a paraphrase)
 * against a schema that has already had {@code db/init/051-job-company.sql} applied, seeded
 * with the QAE's own fixture, mirroring {@link CompanyMigrationComponentTest}'s mechanism.
 *
 * <p>052 is now a curated own-site-icon backfill: it fills a row's {@code logo_url} only when
 * the row's slug matches a curated company AND the row is an untouched empty slot. The fixture
 * therefore covers a curated fill (including the {@code datadog} to {@code datadoghq.com}
 * correctness that the old slug-guess got wrong), an uncurated row that stays NULL, a curated
 * row that is manually-edited (guard wins over curation), and rows that already have a logo.
 */
@QuarkusTest
@TestProfile(CompanyLogoBackfillMigrationTestProfile.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Company Logo Backfill Migration Component Test (db/init/052-company-logo-backfill.sql, Story #429)")
class CompanyLogoBackfillMigrationComponentTest {

    private static final Path MIGRATION_051 = Path.of("../db/init/051-job-company.sql");
    private static final Path MIGRATION_052 = Path.of("../db/init/052-company-logo-backfill.sql");

    @Inject
    DataSource dataSource;

    @BeforeAll
    void applyPrerequisitesSeedFixtureAndRunMigrationOnce() throws IOException, SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(MIGRATION_051));

            // Fixture seeded directly into crawler.company (051's own backfill inserts nothing:
            // init-logo-migration-test.sql seeds crawler.pull_target with zero rows). Slugs are
            // the exact 051-mirror slugs of the curated names, so 052's name-keyed match lands.
            statement.execute(
                    "INSERT INTO crawler.company (slug, name, logo_url, manually_edited) VALUES "
                            // curated, empty slot -> filled
                            + "('stripe', 'Stripe', NULL, false),"
                            // curated, empty slot -> filled with the CORRECT datadoghq.com domain
                            + "('datadog', 'Datadog', NULL, false),"
                            // NOT curated, empty slot -> stays NULL (initials chip, #430 fills it)
                            + "('globex', 'Globex', NULL, false),"
                            // curated name BUT manually-edited + cleared -> guard wins, stays NULL
                            + "('qonto', 'Qonto', NULL, true),"
                            // non-manual, already has a logo -> untouched
                            + "('okta', 'Okta', 'https://old-cdn.example/okta.png', false),"
                            // manually-edited WITH a curated-slug + a logo -> untouched
                            + "('malt', 'Malt', 'https://curated.example/malt.png', true)");

            // First pass once here so every read-only @Test can run in any order. MIG-05
            // re-applies it a second time itself to prove idempotency.
            statement.execute(Files.readString(MIGRATION_052));
        }
    }

    private String logoUrlOf(Statement statement, String slug) throws SQLException {
        try (ResultSet rs = statement.executeQuery(
                "SELECT logo_url FROM crawler.company WHERE slug = '" + slug + "'")) {
            assertThat(rs.next()).as("row for slug '" + slug + "' must exist").isTrue();
            return rs.getString("logo_url");
        }
    }

    private void applyMigration052(Statement statement) throws IOException, SQLException {
        statement.execute(Files.readString(MIGRATION_052));
    }

    // ── QAE-429-MIG-01 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-429-MIG-01 (AC-429-13): a curated, empty-slot row is filled with its own-site "
            + "icon - including datadog -> datadoghq.com, the exact case the old slug-guess got wrong")
    void curatedEmptyRowsAreFilledWithOwnSiteIcon() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            assertThat(logoUrlOf(statement, "stripe")).isEqualTo("https://stripe.com/favicon.ico");
            assertThat(logoUrlOf(statement, "datadog")).isEqualTo("https://datadoghq.com/favicon.ico");
        }
    }

    // ── QAE-429-MIG-02 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-429-MIG-02 (AC-429-13): a row whose slug is NOT curated stays NULL - the "
            + "migration fills only known companies, everything else degrades to the initials chip")
    void uncuratedRowStaysNull() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            assertThat(logoUrlOf(statement, "globex")).isNull();
        }
    }

    // ── QAE-429-MIG-03 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-429-MIG-03 (AC-429-11): a manually-edited, intentionally-cleared row stays "
            + "NULL even though its slug IS curated - the guard excludes it on manually_edited alone")
    void manuallyEditedClearedRowStaysNullDespiteBeingCurated() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            assertThat(logoUrlOf(statement, "qonto")).isNull();
        }
    }

    // ── QAE-429-MIG-04 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-429-MIG-04 (AC-429-12): a row that already has ANY logo is untouched - both "
            + "the non-manual 'okta' and the manually-edited 'malt' keep their existing logo_url")
    void rowsThatAlreadyHaveALogoAreUntouched() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            assertThat(logoUrlOf(statement, "okta")).isEqualTo("https://old-cdn.example/okta.png");
            assertThat(logoUrlOf(statement, "malt")).isEqualTo("https://curated.example/malt.png");
        }
    }

    // ── QAE-429-MIG-05 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-429-MIG-05 (AC-429-14): re-running the literal 052 file a second time changes "
            + "nothing - every row's logo_url is byte-identical, no error, no new rows")
    void reRunningMigrationChangesNothing() throws SQLException, IOException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {

            String[] slugs = {"stripe", "datadog", "globex", "qonto", "okta", "malt"};
            String[] before = new String[slugs.length];
            for (int i = 0; i < slugs.length; i++) {
                before[i] = logoUrlOf(statement, slugs[i]);
            }

            int countBefore = countCompanyRows(statement);

            applyMigration052(statement);

            for (int i = 0; i < slugs.length; i++) {
                assertThat(logoUrlOf(statement, slugs[i]))
                        .as("slug '%s' must be byte-identical across the idempotent re-run", slugs[i])
                        .isEqualTo(before[i]);
            }
            assertThat(countCompanyRows(statement))
                    .as("052 is UPDATE-only: no duplicate rows on re-run")
                    .isEqualTo(countBefore);
        }
    }

    private int countCompanyRows(Statement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM crawler.company")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // ── QAE-429-MIG-06 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-429-MIG-06 (AC-429-15): the migration's own trailing verification SELECT is "
            + "mechanically checkable - (total, with_logo) = (6, 4) for this fixture")
    void ownVerificationSelectReportsMechanicalCounts() throws SQLException, IOException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            applyMigration052(statement);

            try (ResultSet rs = statement.executeQuery(
                    "SELECT (SELECT COUNT(*) FROM crawler.company) AS total,"
                            + " (SELECT COUNT(*) FROM crawler.company WHERE logo_url IS NOT NULL) AS with_logo")) {
                assertThat(rs.next()).isTrue();
                int total = rs.getInt("total");
                int withLogo = rs.getInt("with_logo");
                assertThat(total).isEqualTo(6);
                assertThat(withLogo).isEqualTo(4);

                int nullLogoCount;
                try (ResultSet nullRs = statement.executeQuery(
                        "SELECT COUNT(*) FROM crawler.company WHERE logo_url IS NULL")) {
                    nullRs.next();
                    nullLogoCount = nullRs.getInt(1);
                }
                assertThat(withLogo).isEqualTo(total - nullLogoCount);
            }
        }
    }
}
