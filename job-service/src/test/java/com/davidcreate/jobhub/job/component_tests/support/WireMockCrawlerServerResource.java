package com.davidcreate.jobhub.job.component_tests.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

/**
 * Starts a standalone WireMock server standing in for crawler-service's internal
 * trigger-request endpoints ({@code POST /internal/trigger-requests} and
 * {@code POST /internal/trigger-requests/{kind}/cancel}, ADR 0033). job-service's
 * outbound REST client is pointed at it via {@code quarkus.rest-client.crawler-service.url}.
 */
public class WireMockCrawlerServerResource implements QuarkusTestResourceLifecycleManager {

    private static WireMockServer server;

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
        return Map.of("quarkus.rest-client.crawler-service.url", server.baseUrl());
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
