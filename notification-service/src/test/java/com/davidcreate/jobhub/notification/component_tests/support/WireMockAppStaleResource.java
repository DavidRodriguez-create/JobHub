package com.davidcreate.jobhub.notification.component_tests.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

/**
 * Standalone WireMock server standing in for application-service's stale/ghosted-alert
 * internal endpoints used by {@code StaleApplicationRestClient}:
 *   GET  /internal/applications/stale
 *   PUT  /internal/applications/{id}/status
 *
 * Points {@code quarkus.rest-client.app-stale.url} at the stub.
 */
public class WireMockAppStaleResource implements QuarkusTestResourceLifecycleManager {

    private static WireMockServer server;

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
        return Map.of("quarkus.rest-client.app-stale.url", server.baseUrl());
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
