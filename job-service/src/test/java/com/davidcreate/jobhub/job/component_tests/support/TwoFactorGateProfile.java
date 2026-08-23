package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

/**
 * Same effective config as the default profile ({@code enabled=true}) but forces a
 * separate Quarkus test context (fresh {@code drop-and-create} DB) plus the WireMock
 * auth-service resource, for {@code AdminTriggerTwoFactorGateComponentTest} and
 * {@code AdminTriggerStatusTwoFactorComponentTest} (ADR 0019) — isolated from
 * {@code AdminTriggerResourceComponentTest}'s ordered crawl+enrichment lifecycle.
 */
public class TwoFactorGateProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("jobhub.admin.trigger.enabled", "true");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(WireMockAuthServerResource.class),
                new TestResourceEntry(WireMockCrawlerServerResource.class));
    }
}
