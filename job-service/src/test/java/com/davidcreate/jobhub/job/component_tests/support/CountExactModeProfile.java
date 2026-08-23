package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * {@code job.search.count.mode=exact} with {@code exact-threshold=0} (Story #331 /
 * ADR 0018). The adversarially-low threshold proves nothing under {@code hybrid}
 * mode, but combined with {@code mode=exact} it makes AC-331-8's claim checkable:
 * if {@code exact-threshold} were still honoured, every query would take the
 * estimate branch; {@code mode=exact} must fully bypass it regardless.
 */
public class CountExactModeProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "job.search.count.mode", "exact",
                "job.search.count.exact-threshold", "0");
    }
}
