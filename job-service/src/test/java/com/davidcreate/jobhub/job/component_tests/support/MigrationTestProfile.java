package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Story #428 (QAE-428-MIG-01/02): forces its own fresh DevServices Postgres container,
 * seeded with a PRE-051 schema shape ({@code crawler.pull_target} with only
 * {@code company_name}/{@code company_logo_url}, no {@code company_id}, no
 * {@code crawler.company} table) via a dedicated init script, and Hibernate schema
 * management turned off entirely - this profile's test executes the literal
 * {@code db/init/051-job-company.sql} file itself, not Hibernate's entity-driven
 * drop-and-create (which never runs anything under {@code db/init/}).
 */
public class MigrationTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.datasource.devservices.init-script-path", "db/init-migration-test.sql",
                "quarkus.hibernate-orm.schema-management.strategy", "none",
                "quarkus.hibernate-orm.sql-load-script", "no-file");
    }
}
