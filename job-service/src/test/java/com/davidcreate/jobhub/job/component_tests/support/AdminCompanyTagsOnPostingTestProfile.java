package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Story #430 (QAE-430-T-01/02): forces its own fresh DevServices Postgres container.
 * {@code AdminCompanyTagsOnPostingComponentTest} seeds two brand-new {@code job_post} rows
 * (Tags Demo Co's two postings) in its own {@code @BeforeAll}; several pre-existing tests
 * (e.g. {@code JobResourceComponentTest}) hardcode the unfiltered job-posting
 * totalElements/X-Total-Count against the shared no-{@code @TestProfile} baseline, so this
 * story's extra postings must never land in that shared container.
 */
public class AdminCompanyTagsOnPostingTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.hibernate-orm.sql-load-script", "db/test-seeds.sql");
    }
}
