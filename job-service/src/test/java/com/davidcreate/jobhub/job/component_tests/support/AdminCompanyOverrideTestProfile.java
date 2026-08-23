package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Story #430 (QAE-430-OV-01/02): forces its own fresh DevServices Postgres container,
 * isolated from the shared no-{@code @TestProfile} instance every other component test in
 * this module uses. {@code AdminCompanyOverrideComponentTest} calls the production
 * {@code ResolveCompaniesUseCase.resolvePending()} directly (a genuine, committed write, same
 * pattern as {@code CompanyLogoResolutionTestProfile} from story #429) after an admin PUT
 * edit, so it must not share a container with tests whose assertions are pinned to fixed
 * totals over the shared seed fixture.
 */
public class AdminCompanyOverrideTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        // Re-declaring the already-default sql-load-script value is enough to give this
        // profile its own identity (and therefore its own fresh container/seed run)
        // without changing any actual behaviour - see the class Javadoc.
        return Map.of("quarkus.hibernate-orm.sql-load-script", "db/test-seeds.sql");
    }
}
