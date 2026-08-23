package com.davidcreate.jobhub.job.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

/**
 * {@code jobhub.admin.trigger.enabled=false} (Story #7 / ADR 0003 — FLAG-2: GET
 * /jobs/admin/triggers/status must remain reachable when the toggle is off).
 *
 * <p>Pairs with {@link WireMockAuthServerResource}: even with triggering disabled,
 * {@code GET /jobs/admin/triggers/status} still resolves the caller's 2FA state
 * (ADR 0019), and {@code POST /jobs/admin/triggers} must reject on the disabled gate
 * before ever reaching the 2FA gate (BR-384-4, TC-384-J22).
 */
public class TriggerDisabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("jobhub.admin.trigger.enabled", "false");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(WireMockAuthServerResource.class));
    }
}
