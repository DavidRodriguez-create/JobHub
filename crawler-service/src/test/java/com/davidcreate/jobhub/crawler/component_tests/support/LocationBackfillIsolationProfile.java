package com.davidcreate.jobhub.crawler.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Forces its own fresh drop-and-create DevServices DB (Quarkus restarts the test application
 * whenever the active {@link QuarkusTestProfile} changes), matching the job-service {@code
 * LowFacetCacheTtlProfile}/{@code LowFacetStampTtlProfile} precedent. {@link
 * com.davidcreate.jobhub.crawler.component_tests.LocationNormalizationBackfillComponentTest}
 * walks and rewrites the ENTIRE {@code crawler.job_post} table (unlike most component tests,
 * which only touch rows they themselves created), so it must not share the default-profile
 * instance with every other component test class in this module.
 */
public class LocationBackfillIsolationProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("crawler.maintenance.normalize-locations-batch-size", "999");
    }
}
