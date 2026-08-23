package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Story #484 (QAE-484-JS-MIG-01): forces its own fresh DevServices Postgres container,
 * seeded with a POST-051 schema shape via the shared dedicated init script, and Hibernate
 * schema management turned off entirely - mirrors {@link CompanyLogoBackfillMigrationTestProfile}
 * exactly, except the test running against this profile applies {@code
 * db/init/051-job-company.sql}, then {@code db/init/053-company-enrichment-tracking.sql} (to
 * put the columns/index in place, exactly as prod history did), THEN the literal {@code
 * db/init/057-company-drop-enrichment-tracking.sql} file under test, proving the drop.
 */
public class CompanyEnrichmentDropMigrationTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.datasource.devservices.init-script-path", "db/init-logo-migration-test.sql",
                "quarkus.hibernate-orm.schema-management.strategy", "none",
                "quarkus.hibernate-orm.sql-load-script", "no-file");
    }
}
