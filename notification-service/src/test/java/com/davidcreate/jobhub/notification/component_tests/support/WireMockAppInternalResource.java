package com.davidcreate.jobhub.notification.component_tests.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

/**
 * Standalone WireMock server standing in for application-service's
 * {@code GET /internal/users/{userId}/interest-profile} (Story #80 / ADR 0008).
 * notification-service's {@code app-internal} REST client is pointed at it via
 * {@code quarkus.rest-client.app-internal.url}.
 */
public class WireMockAppInternalResource implements QuarkusTestResourceLifecycleManager {

    private static WireMockServer server;

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
        return Map.of("quarkus.rest-client.app-internal.url", server.baseUrl());
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    public static WireMockServer server() {
        return server;
    }
}
