package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Story #429 (QAE-429-C-01..05): forces its own fresh DevServices Postgres container,
 * isolated from the shared no-@TestProfile instance used by {@code JobResourceComponentTest}
 * / {@code CompanyResolutionComponentTest} (and every other component test in this module
 * that has no {@code @TestProfile}). {@code CompanyLogoResolutionComponentTest} calls the
 * production {@code ResolveCompaniesUseCase.resolvePending()} directly - a genuine, committed
 * write - so it must not share a container with tests whose assertions are pinned to fixed
 * totals over the shared seed (e.g. {@code totalElements == 14}): resolving its own new
 * pull targets there would also resolve the shared fixture's still-pending Northwind Freight
 * target, and any new {@code job_post} row would shift every hardcoded total.
 *
 * <p>This profile's entire purpose is container isolation, not a config change: logos are
 * curated in db/init/052, there is no runtime logo config to override under revised ADR 0024.
 */
public class CompanyLogoResolutionTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        // Re-declaring the already-default sql-load-script value is enough to give this
        // profile its own identity (and therefore its own fresh container/seed run)
        // without changing any actual behaviour - see the class Javadoc.
        return Map.of("quarkus.hibernate-orm.sql-load-script", "db/test-seeds.sql");
    }
}
