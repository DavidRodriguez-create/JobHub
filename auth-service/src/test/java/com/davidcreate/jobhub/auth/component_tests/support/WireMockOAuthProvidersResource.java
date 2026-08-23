package com.davidcreate.jobhub.auth.component_tests.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

/**
 * One shared WireMock server standing in for BOTH google and github's token +
 * userinfo endpoints (story #459, ADR 0027; note 0.2 of the QA doc). auth-service's
 * four provider rest-clients (google-oauth-token, google-oauth-userinfo,
 * github-oauth-token, github-oauth-api) all point at this same instance in tests -
 * their real paths never collide (/token, /v1/userinfo, /login/oauth/access_token,
 * /user, /user/emails), so one server is enough and keeps every OAuth component
 * test class on the same plain @QuarkusTestResource config (no @TestProfile reboot).
 */
public class WireMockOAuthProvidersResource implements QuarkusTestResourceLifecycleManager {

    private static WireMockServer server;

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
        String baseUrl = server.baseUrl();
        return Map.of(
                "quarkus.rest-client.google-oauth-token.url", baseUrl,
                "quarkus.rest-client.google-oauth-userinfo.url", baseUrl,
                "quarkus.rest-client.github-oauth-token.url", baseUrl,
                "quarkus.rest-client.github-oauth-api.url", baseUrl);
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
