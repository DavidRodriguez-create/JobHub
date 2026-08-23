package com.davidcreate.jobhub.auth.component_tests.support;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/** Stub builders for the WireMock server behind {@link WireMockOAuthProvidersResource}. */
public final class OAuthProviderStubs {

    private OAuthProviderStubs() {
    }

    public static void stubGoogleHappyPath(String sub, String email, boolean emailVerified) {
        stubGoogleToken(200, """
                {"access_token":"google-access-token","token_type":"Bearer","expires_in":3600}
                """);
        stubGoogleUserInfo(200, """
                {"sub":"%s","email":"%s","email_verified":%s,"given_name":"Ada","family_name":"Lovelace"}
                """.formatted(sub, email, emailVerified));
    }

    public static void stubGoogleToken(int status, String body) {
        server().stubFor(post(urlEqualTo("/token"))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    public static void stubGoogleUserInfo(int status, String body) {
        server().stubFor(get(urlEqualTo("/v1/userinfo"))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    public static void stubGithubHappyPath(long id, String login, String name, String email,
                                            boolean primary, boolean verified) {
        stubGithubToken(200, """
                {"access_token":"github-access-token","token_type":"bearer","scope":"read:user,user:email"}
                """);
        stubGithubUser(200, """
                {"id":%d,"login":"%s","name":%s}
                """.formatted(id, login, name == null ? "null" : "\"" + name + "\""));
        stubGithubEmails(200, """
                [{"email":"%s","primary":%s,"verified":%s}]
                """.formatted(email, primary, verified));
    }

    public static void stubGithubToken(int status, String body) {
        server().stubFor(post(urlEqualTo("/login/oauth/access_token"))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    public static void stubGithubUser(int status, String body) {
        server().stubFor(get(urlEqualTo("/user"))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    public static void stubGithubEmails(int status, String body) {
        server().stubFor(get(urlEqualTo("/user/emails"))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    // GH-EXCHANGE-1 (ADR 0028, Decision 4): GitHub's own shape for a failed
    // exchange - HTTP 200, an `error` field, and no access token at all.
    public static void stubGithubTokenBlankAccessToken(String error, String errorDescription) {
        stubGithubToken(200, """
                {"error":"%s","error_description":"%s"}
                """.formatted(error, errorDescription));
    }

    // GH-EXCHANGE-1 variant: `access_token` present but an empty string.
    public static void stubGithubTokenEmptyAccessToken(String error) {
        stubGithubToken(200, """
                {"access_token":"","error":"%s"}
                """.formatted(error));
    }

    public static void resetAll() {
        server().resetAll();
    }

    private static WireMockServer server() {
        return WireMockOAuthProvidersResource.server();
    }
}
