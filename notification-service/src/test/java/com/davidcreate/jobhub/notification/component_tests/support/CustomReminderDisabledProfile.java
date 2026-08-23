package com.davidcreate.jobhub.notification.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class CustomReminderDisabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("notification.custom-reminder.enabled", "false");
    }
}
