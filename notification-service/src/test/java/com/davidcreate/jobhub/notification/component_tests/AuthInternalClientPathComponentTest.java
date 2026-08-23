package com.davidcreate.jobhub.notification.component_tests;

import com.davidcreate.jobhub.notification.component_tests.support.WireMockAuthInternalResource;
import com.davidcreate.jobhub.notification.domain.port.out.UserEmailGateway;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * EM-C-001 (story #211, req 6): regression pin for the auth-internal client base path.
 *
 * notification-service's {@code auth-internal} REST client must call
 * {@code GET /auth/internal/users/emails} (auth-service runs at
 * {@code quarkus.http.root-path=/auth}), not {@code /internal/users/emails}.
 *
 * This test stubs ONLY the correct, prefixed path; it fails against the old
 * unprefixed path and passes once {@code AuthInternalRestClient} is fixed.
 */
@QuarkusTest
@QuarkusTestResource(WireMockAuthInternalResource.class)
@DisplayName("AuthInternalRestClient path Component Tests")
class AuthInternalClientPathComponentTest {

    private static final String EMAILS_PATH = "/auth/internal/users/emails";
    private static final UUID USER_ID = UUID.fromString("ef000000-0000-0000-0000-000000000001");
    private static final String EXPECTED_EMAIL = "em-c-001@example.com";

    @Inject
    UserEmailGateway userEmailGateway;

    @BeforeEach
    void resetWireMock() {
        authInternal().resetAll();
    }

    private WireMockServer authInternal() {
        return WireMockAuthInternalResource.server();
    }

    private void stubEmailBatch() {
        authInternal().stubFor(get(urlPathEqualTo(EMAILS_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "emails": [
                                    {"userId": "%s", "email": "%s"}
                                  ]
                                }
                                """.formatted(USER_ID, EXPECTED_EMAIL))));
    }

    // EM-C-001
    @Test
    @DisplayName("EM-C-001: fetchEmails calls GET /auth/internal/users/emails and resolves the email")
    void fetchEmailsCallsCorrectlyPrefixedPath() {
        stubEmailBatch();

        Map<UUID, String> result = userEmailGateway.fetchEmails(Set.of(USER_ID));

        assertThat(result).isNotEmpty();
        assertThat(result).containsEntry(USER_ID, EXPECTED_EMAIL);
    }
}
