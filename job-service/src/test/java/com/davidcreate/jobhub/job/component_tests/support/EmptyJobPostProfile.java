package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Forces its own fresh drop-and-create DevServices DB (Story #332 / AC-332-15), cache
 * left enabled with default TTLs. The zero-row {@code crawler.job_post} state itself is
 * a committed {@code DELETE} the consuming test class runs before each test, not
 * anything this profile does directly (see {@code DedupeStatesProfile}'s Javadoc for the
 * established "distinct @TestProfile forces isolation" precedent).
 */
public class EmptyJobPostProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("job.search.facets.cache.enabled", "true");
    }
}
