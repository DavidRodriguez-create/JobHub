package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * {@code job.search.facets.cache.ttl=PT5S} (short, but generous enough to absorb a cold
 * first HTTP request against a freshly-booted DevServices instance), {@code
 * job.search.facets.stamp.ttl} left long ({@code PT120S}) so the generation stamp
 * provably never re-reads within the test window, isolating the per-entry TTL as the
 * sole invalidation mechanism under test (Story #332 / ADR 0020, FC332-C-15). Forces its
 * own fresh drop-and-create DevServices DB, matching {@link DedupeStatesProfile}'s
 * established precedent.
 */
public class LowFacetCacheTtlProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "job.search.facets.cache.ttl", "PT5S",
                "job.search.facets.stamp.ttl", "PT120S");
    }
}
