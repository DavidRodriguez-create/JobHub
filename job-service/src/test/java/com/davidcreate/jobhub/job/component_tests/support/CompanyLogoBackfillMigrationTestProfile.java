package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Story #429 (QAE-429-MIG-01..06): forces its own fresh DevServices Postgres container,
 * seeded with a POST-051 schema shape ({@code crawler.company} does not exist yet, empty
 * {@code crawler.pull_target}) via a dedicated init script, and Hibernate schema management
 * turned off entirely - mirrors {@link MigrationTestProfile} exactly, except the dedicated
 * init script here leaves room for {@code db/init/051-job-company.sql} to be applied FIRST
 * (creating {@code crawler.company}), then a small fixture seeded directly, THEN the
 * literal {@code db/init/052-company-logo-backfill.sql} file under test.
 */
public class CompanyLogoBackfillMigrationTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.datasource.devservices.init-script-path", "db/init-logo-migration-test.sql",
                "quarkus.hibernate-orm.schema-management.strategy", "none",
                "quarkus.hibernate-orm.sql-load-script", "no-file");
    }
}
