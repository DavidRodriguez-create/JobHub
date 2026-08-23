package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * {@code jobhub.http.slow-request.threshold-ms=0} (Story #328, slow-request WARN).
 * With a zero threshold every request, even a fast one, crosses the {@code >=}
 * guard, so a single {@code GET /jobs} deterministically produces exactly one WARN.
 */
public class SlowRequestWarnProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "jobhub.http.slow-request.enabled", "true",
                "jobhub.http.slow-request.threshold-ms", "0");
    }
}
