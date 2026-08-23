package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

/**
 * Same effective config as {@link TwoFactorGateProfile} ({@code enabled=true} +
 * WireMock auth-service resource) but forces its own separate Quarkus test context
 * (fresh {@code drop-and-create} DB) for
 * {@code AdminTriggerStatusTwoFactorComponentTest} (ADR 0019), isolated from
 * {@code AdminTriggerTwoFactorGateComponentTest}'s inserted trigger-request rows.
 */
public class TwoFactorStatusProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("jobhub.admin.trigger.enabled", "true");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(WireMockAuthServerResource.class));
    }
}
