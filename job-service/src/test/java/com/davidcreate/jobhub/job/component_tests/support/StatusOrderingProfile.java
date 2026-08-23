package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Same effective config as the default profile ({@code enabled=true}) but forces a
 * separate Quarkus test context so
 * {@link com.davidcreate.jobhub.job.component_tests.AdminTriggerStatusOrderingComponentTest}
 * gets its own fresh {@code drop-and-create} DB (Story #7 / ADR 0003 — QA
 * end-review gap J-C-23: most-recent-per-kind ordering).
 */
public class StatusOrderingProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("jobhub.admin.trigger.enabled", "true");
    }
}
