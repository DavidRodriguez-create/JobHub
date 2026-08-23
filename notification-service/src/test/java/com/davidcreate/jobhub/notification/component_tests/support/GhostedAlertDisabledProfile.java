package com.davidcreate.jobhub.notification.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that disables the ghosted-alert scheduler kill switch so the component
 * test for GA-NS-19 can verify that the stale query is never made when the feature is off.
 */
public class GhostedAlertDisabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("notification.ghosted.enabled", "false");
    }
}
