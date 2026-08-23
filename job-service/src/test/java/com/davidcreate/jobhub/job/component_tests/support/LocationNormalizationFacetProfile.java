package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Forces a separate Quarkus test context (fresh drop-and-create DB, {@code test-seeds.sql}
 * re-applied) so {@link
 * com.davidcreate.jobhub.job.component_tests.LocationNormalizationFacetComponentTest} can seed
 * its own already-canonical {@code crawler.job_post} rows (story #408, ADR 0021) without
 * perturbing the exact row-count assertions ({@code totalElements}, per-country facet counts)
 * that every other component test class in this module depends on in the shared default-profile
 * instance. Matches the {@code DedupeStatesProfile} precedent (a config override forces
 * isolation).
 *
 * <p>The facet cache is disabled entirely (not just given a low TTL): each test method inserts
 * a fresh row and immediately reads {@code GET /jobs/facets} within the same test run, faster
 * than the generation-stamp/TTL windows the other facet-cache tests exercise deliberately, so
 * this test class needs every read to be live, not a story #408 concern of its own.
 */
public class LocationNormalizationFacetProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("job.search.facets.cache.enabled", "false");
    }
}
