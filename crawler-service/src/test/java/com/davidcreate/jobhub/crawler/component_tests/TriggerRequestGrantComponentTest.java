package com.davidcreate.jobhub.crawler.component_tests;

import io.quarkus.test.junit.QuarkusTest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story #582 (ADR 0033), AC-12: executes the LITERAL {@code db/init/061} file (byte-for-byte,
 * not a paraphrase) against a schema where {@code job_user} first holds the pre-061 grants
 * from {@code db/init/016}:46 (SELECT, INSERT) and {@code db/init/018}:20 (UPDATE), mirroring
 * {@code CompanyMigrationComponentTest}'s approach in job-service.
 *
 * <p>{@code trigger_request} is created by Hibernate (drop-and-create on the test datasource,
 * connected as the DevServices superuser), so the grants/revoke can only run AFTER the
 * {@code @QuarkusTest} context has started -- not from the init script, which runs before any
 * table exists. Privilege checks then run as {@code job_user} via {@code SET ROLE}: the
 * DevServices connection is a Postgres superuser, so it can switch role within a session
 * without a separate login. Each statement runs on an autocommit connection so a
 * permission-denied failure aborts only that one statement.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Trigger Request Grant Component Test (db/init/061, story #582)")
class TriggerRequestGrantComponentTest {

    private static final Path MIGRATION_FILE =
            Path.of("../db/init/061-crawler-trigger-request-revoke-job-user.sql");

    @jakarta.inject.Inject
    DataSource dataSource;

    private boolean setupDone = false;

    @BeforeEach
    void applyGrantsThenMigrationOnce() throws SQLException, IOException {
        if (setupDone) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    DO $$
                    BEGIN
                        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'job_user') THEN
                            CREATE ROLE job_user LOGIN PASSWORD 'job_password';
                        END IF;
                    END
                    $$;
                    """);
            statement.execute("GRANT USAGE ON SCHEMA crawler TO job_user");
            // Mirrors db/init/016-crawler-trigger-request.sql:46
            statement.execute("GRANT SELECT, INSERT ON crawler.trigger_request TO job_user");
            // Mirrors db/init/018-crawler-trigger-cancel.sql:20
            statement.execute("GRANT UPDATE ON crawler.trigger_request TO job_user");

            String migrationSql = Files.readString(MIGRATION_FILE);
            statement.execute(migrationSql);
        }
        setupDone = true;
    }

    // ── TR-14 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TR-14: migration 061 applied - job_user INSERTs crawler.trigger_request - "
            + "rejected, insufficient privilege")
    void jobUserInsertIsRejected() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET ROLE job_user");
                try {
                    assertThatThrownBy(() -> statement.execute(
                            "INSERT INTO crawler.trigger_request (id, kind, status) "
                                    + "VALUES (gen_random_uuid(), 'crawl', 'queued')"))
                            .isInstanceOf(SQLException.class)
                            .hasMessageContaining("permission denied");
                } finally {
                    statement.execute("RESET ROLE");
                }
            }
        }
    }

    // ── TR-15 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TR-15: migration 061 applied - job_user UPDATEs crawler.trigger_request - "
            + "rejected, insufficient privilege")
    void jobUserUpdateIsRejected() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET ROLE job_user");
                try {
                    assertThatThrownBy(() -> statement.execute(
                            "UPDATE crawler.trigger_request SET status = 'cancelled' WHERE false"))
                            .isInstanceOf(SQLException.class)
                            .hasMessageContaining("permission denied");
                } finally {
                    statement.execute("RESET ROLE");
                }
            }
        }
    }

    // ── TR-16 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TR-16: migration 061 applied - job_user SELECTs crawler.trigger_request - "
            + "succeeds, rows returned")
    void jobUserSelectSucceeds() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET ROLE job_user");
                try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM crawler.trigger_request")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(0);
                } finally {
                    statement.execute("RESET ROLE");
                }
            }
        }
    }
}
