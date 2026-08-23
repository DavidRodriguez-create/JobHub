package com.davidcreate.jobhub.job.component_tests.support;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/**
 * Stub helpers for auth-service's internal 2FA endpoints (ADR 0019). Every
 * {@code GET /jobs/admin/triggers/status} and {@code POST /jobs/admin/triggers} call
 * now resolves the caller's 2FA state service-to-service (QAE note 0.1) — component
 * test classes that aren't themselves exercising the 2FA gate use
 * {@link #stubNoTwoFactorForEveryAdmin(WireMockServer)} in a {@code @BeforeEach} so
 * their admins keep behaving like today's no-2FA admin, unrelated to what each class
 * actually asserts.
 */
public final class TwoFactorStubs {

    private static final String STATUS_PATH_PATTERN = "/auth/internal/users/.*/two-factor";
    private static final String VERIFY_PATH = "/auth/internal/two-factor/verify";

    private TwoFactorStubs() {
    }

    /** Every admin reports as not having 2FA enabled; verify always authorizes as {@code not_enrolled}. */
    public static void stubNoTwoFactorForEveryAdmin(WireMockServer server) {
        server.stubFor(get(urlPathMatching(STATUS_PATH_PATTERN))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"twoFactorEnabled\":false}")));
        server.stubFor(post(urlEqualTo(VERIFY_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"outcome\":\"not_enrolled\"}")));
    }
}
