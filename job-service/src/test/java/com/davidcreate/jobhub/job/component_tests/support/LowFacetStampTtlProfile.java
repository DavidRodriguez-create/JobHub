package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * {@code job.search.facets.stamp.ttl=PT1S} (short), {@code job.search.facets.cache.ttl}
 * left long ({@code PT120S}) so the per-entry TTL backstop never fires within the test
 * window, isolating the generation stamp as the sole invalidation mechanism under test
 * (Story #332 / ADR 0020, FC332-C-13/14). Forces its own fresh drop-and-create
 * DevServices DB, matching {@link DedupeStatesProfile}'s established precedent.
 */
public class LowFacetStampTtlProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "job.search.facets.stamp.ttl", "PT1S",
                "job.search.facets.cache.ttl", "PT120S");
    }
}
