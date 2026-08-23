package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Same effective config as the default profile ({@code enabled=true}) but forces a
 * separate Quarkus test context so
 * {@link com.davidcreate.jobhub.job.component_tests.AdminTriggerDedupeStatesComponentTest}
 * gets its own fresh {@code drop-and-create} DB (Story #7 / ADR 0003 — QA
 * end-review gaps J-C-08/J-C-12).
 *
 * <p>{@code AdminTriggerResourceComponentTest} runs an ordered crawl+enrichment
 * lifecycle that ends with both kinds {@code queued} in the shared default-profile
 * DB; isolating dedupe-state cases (running/terminal) into their own profile avoids
 * cross-class interference with that lifecycle's global per-kind dedupe checks.
 */
public class DedupeStatesProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("jobhub.admin.trigger.enabled", "true");
    }
}
