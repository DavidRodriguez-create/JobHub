package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Story #430 (QAE-430-U-02/U-09/U-10): forces its own fresh DevServices Postgres container.
 * {@code AdminCompanyUpdateComponentTest} deliberately mutates two rows that OTHER,
 * pre-existing test files in the shared no-{@code @TestProfile} container assert stay
 * unchanged forever: the real "Stripe" company (QAE-430-U-02, the edit-appears-on-every-
 * posting proof, {@code JobResourceComponentTest} asserts {@code manuallyEdited: false} on
 * Stripe's own postings) and "Acme Only" (QAE-430-U-09/U-10, {@code
 * CompanyResolutionComponentTest} asserts the same). Isolating this whole file avoids that
 * cross-file pollution without inventing a synthetic stand-in for either fixture.
 */
public class AdminCompanyUpdateTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.hibernate-orm.sql-load-script", "db/test-seeds.sql");
    }
}
