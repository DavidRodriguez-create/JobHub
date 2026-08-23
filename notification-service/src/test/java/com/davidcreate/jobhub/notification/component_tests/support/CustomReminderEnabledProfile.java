package com.davidcreate.jobhub.notification.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class CustomReminderEnabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        // Keep the feature flag on so the scheduler's enabled-guard does not short-circuit.
        // Disable the Quarkus scheduler engine so the @Scheduled cron never auto-fires during
        // the test; each test case invokes scheduler.run() manually and asserts exact call counts.
        // Without this, the aggressive test cron (every minute) can fire concurrently with a
        // manual run() on CI, causing double mailer invocations and a flaky verify(times(1)).
        return Map.of(
                "notification.custom-reminder.enabled", "true",
                "quarkus.scheduler.enabled", "false"
        );
    }
}
