package com.davidcreate.jobhub.job.component_tests.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

/**
 * Starts a standalone WireMock server standing in for auth-service's internal 2FA
 * endpoints ({@code GET /auth/internal/users/{userId}/two-factor} and
 * {@code POST /auth/internal/two-factor/verify}, ADR 0019). job-service's outbound
 * REST client is pointed at it via {@code quarkus.rest-client.auth-service.url}.
 */
public class WireMockAuthServerResource implements QuarkusTestResourceLifecycleManager {

    private static WireMockServer server;

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
        return Map.of("quarkus.rest-client.auth-service.url", server.baseUrl());
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
