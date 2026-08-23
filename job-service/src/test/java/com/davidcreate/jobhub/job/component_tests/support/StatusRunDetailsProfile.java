package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Same effective config as the default profile ({@code enabled=true}) but forces a
 * separate Quarkus test context so
 * {@link com.davidcreate.jobhub.job.component_tests.AdminTriggerStatusRunDetailsComponentTest}
 * gets its own fresh {@code drop-and-create} DB (Story #7 / ADR 0003 — QA
 * end-review gaps J-C-24/J-C-25: per-field run-info shape for a succeeded crawl
 * run and a failed-most-recent enrichment run).
 */
public class StatusRunDetailsProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("jobhub.admin.trigger.enabled", "true");
    }
}
