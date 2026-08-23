package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Same empty-table setup as {@link EmptyJobPostProfile}, own fresh drop-and-create
 * DevServices DB, but with the facet cache disabled (Story #332 / AC-332-15/18).
 */
public class EmptyJobPostCacheDisabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("job.search.facets.cache.enabled", "false");
    }
}
