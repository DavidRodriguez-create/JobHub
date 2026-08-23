package com.davidcreate.jobhub.notification.component_tests.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

/**
 * Standalone WireMock server standing in for auth-service's
 * {@code GET /auth/internal/users/emails} (Story #80 / ADR 0008; path corrected in
 * story #211 to include auth-service's {@code /auth} root path). notification-service's
 * {@code auth-internal} REST client is pointed at it via
 * {@code quarkus.rest-client.auth-internal.url}.
 */
public class WireMockAuthInternalResource implements QuarkusTestResourceLifecycleManager {

    private static WireMockServer server;

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
        return Map.of("quarkus.rest-client.auth-internal.url", server.baseUrl());
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
