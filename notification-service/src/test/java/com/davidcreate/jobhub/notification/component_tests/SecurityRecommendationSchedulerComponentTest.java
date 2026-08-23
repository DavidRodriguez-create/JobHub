package com.davidcreate.jobhub.notification.component_tests;

import com.davidcreate.jobhub.notification.component_tests.support.WireMockAuthInternalResource;
import com.davidcreate.jobhub.notification.domain.model.NotificationType;
import com.davidcreate.jobhub.notification.domain.model.ReadStatusFilter;
import com.davidcreate.jobhub.notification.domain.port.in.ProcessSecurityRecommendationsUseCase;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Component tests for the security-recommendation use case (Story #208, ticket #225,
 * SR-C-12): round-trips a SECURITY_RECOMMENDATION notification through the real
 * DevServices Postgres, proving the playful copy and the null applicationId hold end to
 * end, not just at the unit/mock level.
 *
 * Injects {@link ProcessSecurityRecommendationsUseCase} directly (rather than the
 * scheduler) since {@code notification.security-recommendation.enabled=false} in the
 * test profile gates the scheduler's kill switch; this case is about the use case/DB
 * round trip, not the cron kill switch (already covered by
 * {@code SecurityRecommendationSchedulerTest}).
 */
@QuarkusTest
@QuarkusTestResource(WireMockAuthInternalResource.class)
@DisplayName("SecurityRecommendationScheduler Component Tests")
class SecurityRecommendationSchedulerComponentTest {

    private static final String WITHOUT_2FA_PATH = "/auth/internal/users/without-2fa";

    private static final UUID SR_USER_01 = UUID.fromString("aa000000-0000-0000-0000-000000000001");

    @Inject
    ProcessSecurityRecommendationsUseCase processSecurityRecommendationsUseCase;

    @Inject
    com.davidcreate.jobhub.notification.adapter.out.persistence.NotificationPanacheRepository notificationRepository;

    @BeforeEach
    void resetWireMock() {
        authInternal().resetAll();
    }

    @AfterEach
    @Transactional
    void cleanGeneratedNotifications() {
        com.davidcreate.jobhub.notification.adapter.out.persistence.entity.NotificationEntity
                .delete("type = ?1 and userId = ?2",
                        NotificationType.SECURITY_RECOMMENDATION.name(), SR_USER_01);
    }

    private WireMockServer authInternal() {
        return WireMockAuthInternalResource.server();
    }

    private void stubUsersWithoutTwoFactor(UUID... userIds) {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < userIds.length; i++) {
            if (i > 0) {
                ids.append(",");
            }
            ids.append("\"").append(userIds[i]).append("\"");
        }
        authInternal().stubFor(get(urlPathEqualTo(WITHOUT_2FA_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"userIds\": [" + ids + "]}")));
    }

    // SR-C-12: notification row round-trips through real DB with exact new copy and
    // null applicationId (maps to AC-SR-1, AC-SR-2)
    @Test
    @DisplayName("SR-C-12: notification row round-trips through real DB with exact copy and null applicationId")
    void notificationRowRoundTripsWithExactCopyAndNullApplicationId() {
        stubUsersWithoutTwoFactor(SR_USER_01);

        processSecurityRecommendationsUseCase.run();

        var notifications = notificationRepository.findByUserId(SR_USER_01, 0, 10, ReadStatusFilter.ALL);
        assertThat(notifications).anySatisfy(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.SECURITY_RECOMMENDATION);
            assertThat(n.getTitle()).isEqualTo("🛡️ Level up your account security!");
            assertThat(n.getMessage()).isEqualTo(
                    "Two-factor authentication adds a second lock to your account, so a stolen password "
                            + "alone can't get anyone in. It takes about two minutes to set up in Settings, "
                            + "and future-you will thank present-you.");
            assertThat(n.getApplicationId()).isNull();
        });
    }
}
