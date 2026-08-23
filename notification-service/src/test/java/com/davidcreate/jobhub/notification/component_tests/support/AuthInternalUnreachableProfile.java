package com.davidcreate.jobhub.notification.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Points {@code auth-internal} at an unreachable port (Story #80, TC-13b) so the
 * weekly-digest scheduler's batch email-lookup call fails with a connection error,
 * simulating auth-service being down.
 */
public class AuthInternalUnreachableProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.rest-client.auth-internal.url", "http://localhost:1");
    }
}
