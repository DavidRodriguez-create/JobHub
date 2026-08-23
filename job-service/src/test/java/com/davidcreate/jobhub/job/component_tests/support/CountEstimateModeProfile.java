package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * {@code job.search.count.mode=estimate} (Story #331 / ADR 0018). Forces the
 * estimate branch unconditionally regardless of the true row count: the seed
 * fixture only has 11 rows, far below the default {@code exact-threshold=1000},
 * so this is the only way to deterministically exercise the estimate path
 * (AC-331-4/5/6/7/9) without growing the seed to 1000+ rows.
 */
public class CountEstimateModeProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("job.search.count.mode", "estimate");
    }
}
