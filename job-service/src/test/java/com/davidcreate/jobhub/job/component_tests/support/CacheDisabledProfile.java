package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * {@code job.search.count.cache.enabled=false} (Story #331 / ADR 0018) and
 * {@code job.search.facets.cache.enabled=false} (Story #332 / ADR 0020). Forces a
 * separate Quarkus test instance (distinct from the shared default-profile one)
 * with fresh, disabled {@code CountCache}/{@code FacetCache}, so failure-injection
 * tests always reach the mocked {@code JobPostRepository}, never a value cached by
 * an earlier, successful {@code GET /jobs}/{@code GET /jobs/facets} call in a
 * different default-profile test class sharing the same JVM/CDI container.
 */
public class CacheDisabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "job.search.count.cache.enabled", "false",
                "job.search.facets.cache.enabled", "false");
    }
}
