package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * {@code job.search.count.mode=hybrid} with {@code exact-threshold=0} (Story #331 /
 * ADR 0018). A wiring smoke proving {@code exact-threshold} is actually read from
 * config at runtime and drives the hybrid branch, independent of the
 * {@code mode=estimate} override path ({@link CountEstimateModeProfile}).
 */
public class LowCountThresholdProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "job.search.count.mode", "hybrid",
                "job.search.count.exact-threshold", "0");
    }
}
