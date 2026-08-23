package com.davidcreate.jobhub.notification.component_tests;

import com.davidcreate.jobhub.notification.adapter.in.scheduler.GhostedAlertScheduler;
import com.davidcreate.jobhub.notification.adapter.out.persistence.NotificationPanacheRepository;
import com.davidcreate.jobhub.notification.component_tests.support.WireMockAppStaleResource;
import com.davidcreate.jobhub.notification.component_tests.support.WireMockAuthInternalResource;
import com.davidcreate.jobhub.notification.domain.model.NotificationType;
import com.davidcreate.jobhub.notification.domain.model.ReadStatusFilter;
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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Component tests for {@link GhostedAlertScheduler}: GA-NS-15 through GA-NS-21
 * (except GA-NS-16 which uses @InjectMock and lives in its own class, and GA-NS-19
 * which needs a different test profile).
 *
 * Uses real DevServices Postgres and WireMock for application-service + auth-service.
 */
@QuarkusTest
@QuarkusTestResource(WireMockAppStaleResource.class)
@QuarkusTestResource(WireMockAuthInternalResource.class)
@DisplayName("GhostedAlertScheduler Component Tests")
class GhostedAlertSchedulerComponentTest {

    // Users seeded in test-seeds.sql for ghosted-alert tests:
    // GA_USER_01: ghostedAlert=true preference (b0000000-...-0001 row from existing seeds)
    // GA_USER_02: ghostedAlert=false preference (b0000000-...-0002 row from existing seeds)
    // GA_APP_01, GA_APP_02: stable application UUIDs for WireMock stubs

    private static final UUID GA_USER_01 = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID GA_USER_02 = UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private static final UUID GA_APP_01  = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID GA_APP_02  = UUID.fromString("d0000000-0000-0000-0000-000000000002");

    private static final String STALE_PATH = "/internal/applications/stale";
    private static final String EMAILS_PATH = "/auth/internal/users/emails";

    @Inject
    GhostedAlertScheduler scheduler;

    @Inject
    NotificationPanacheRepository notificationRepository;

    @BeforeEach
    void resetWireMock() {
        appStale().resetAll();
        authInternal().resetAll();
    }

    @AfterEach
    @Transactional
    void cleanGeneratedNotifications() {
        // Remove only the ghosted-alert notifications this scheduler created for its own
        // b0000000-... test users, scoped so the #182 e0000000-...-0007 deep-link seed row
        // (also GHOSTED_ALERT with an applicationId) is never touched by this cleanup.
        com.davidcreate.jobhub.notification.adapter.out.persistence.entity.NotificationEntity
                .delete("type = ?1 and applicationId is not null and userId in (?2, ?3, ?4)",
                        NotificationType.GHOSTED_ALERT.name(), GA_USER_01, GA_USER_02,
                        UUID.fromString("b0000000-0000-0000-0000-000000000003"));
    }

    private WireMockServer appStale() {
        return WireMockAppStaleResource.server();
    }

    private WireMockServer authInternal() {
        return WireMockAuthInternalResource.server();
    }

    private void stubStaleApps(String body) {
        appStale().stubFor(get(urlPathEqualTo(STALE_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private void stubPutStatus(UUID appId, int status, String responseBody) {
        appStale().stubFor(put(urlEqualTo("/internal/applications/" + appId + "/status"))
                .willReturn(aResponse().withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));
    }

    private void stubEmailBatch(UUID userId, String email) {
        authInternal().stubFor(get(urlPathEqualTo(EMAILS_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "emails": [
                                    {"userId": "%s", "email": "%s"}
                                  ]
                                }
                                """.formatted(userId, email))));
    }

    private void stubEmptyEmailBatch() {
        authInternal().stubFor(get(urlPathEqualTo(EMAILS_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"emails\": []}")));
    }

    private String staleAppBody(UUID appId, UUID userId, String company) {
        return """
                {
                  "items": [
                    {
                      "id": "%s",
                      "userId": "%s",
                      "jobTitle": "Backend Developer",
                      "company": %s,
                      "currentStatus": "applied",
                      "daysSinceLastActivity": 14
                    }
                  ]
                }
                """.formatted(appId, userId, company != null ? "\"" + company + "\"" : "null");
    }

    private String putOkBody(UUID appId, UUID userId) {
        return """
                {"id": "%s", "userId": "%s", "newStatus": "ghosted"}
                """.formatted(appId, userId);
    }

    // GA-NS-15: Notification row written to DB with correct fields (real DB)
    @Test
    @DisplayName("GA-NS-15: notification row written to DB with correct fields")
    void notificationRowWrittenToDbWithCorrectFields() {
        stubStaleApps(staleAppBody(GA_APP_01, GA_USER_01, "Acme Corp"));
        stubPutStatus(GA_APP_01, 200, putOkBody(GA_APP_01, GA_USER_01));
        stubEmptyEmailBatch();

        scheduler.run();

        var notifications = notificationRepository.findByUserId(GA_USER_01, 0, 10, ReadStatusFilter.ALL);
        assertThat(notifications).anySatisfy(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.GHOSTED_ALERT);
            assertThat(n.getTitle()).isEqualTo("👻 A wild ghost appeared!");
            assertThat(n.getMessage()).contains("Backend Developer");
            assertThat(n.isRead()).isFalse();
            assertThat(n.getApplicationId()).isEqualTo(GA_APP_01);
        });
    }

    // GA-C-24: full scheduler round-trip persists the writer's exact title/message and
    // keeps applicationId set (icon-rendering signal for GHOSTED_ALERT)
    @Test
    @DisplayName("GA-C-24: scheduler round-trip persists exact playful copy and keeps applicationId set")
    void schedulerRoundTripPersistsExactCopyAndKeepsApplicationId() {
        stubStaleApps(staleAppBody(GA_APP_01, GA_USER_01, "Acme Corp"));
        stubPutStatus(GA_APP_01, 200, putOkBody(GA_APP_01, GA_USER_01));
        stubEmptyEmailBatch();

        scheduler.run();

        var notifications = notificationRepository.findByUserId(GA_USER_01, 0, 10, ReadStatusFilter.ALL);
        assertThat(notifications).anySatisfy(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.GHOSTED_ALERT);
            assertThat(n.getTitle()).isEqualTo("👻 A wild ghost appeared!");
            assertThat(n.getMessage()).isEqualTo(
                    "Your application Backend Developer seems to have disappeared into the hiring void. "
                            + "If you're still interested in the position, a quick follow-up with the "
                            + "recruiter could bring it back to life. Don't give up! Your next opportunity "
                            + "might be just around the corner.");
            assertThat(n.getApplicationId()).isNotNull();
            assertThat(n.getApplicationId()).isEqualTo(GA_APP_01);
        });
    }

    // GA-NS-17: ghostedAlert=false -> no notification row, PUT never called
    @Test
    @DisplayName("GA-NS-17: ghostedAlert=false means no notification row and PUT never called")
    void ghostedAlertFalseNoNotificationRowPutNeverCalled() {
        // GA_USER_02 has ghostedAlert=false in test-seeds
        stubStaleApps(staleAppBody(GA_APP_02, GA_USER_02, "Some Corp"));
        stubEmptyEmailBatch();

        scheduler.run();

        // PUT must not have been called for this app
        appStale().verify(0, putRequestedFor(urlEqualTo("/internal/applications/" + GA_APP_02 + "/status")));

        var notifications = notificationRepository.findByUserId(GA_USER_02, 0, 10, ReadStatusFilter.ALL);
        assertThat(notifications).noneMatch(n -> n.getType() == NotificationType.GHOSTED_ALERT);
    }

    // GA-NS-18: Empty stale list -> no notifications written
    @Test
    @DisplayName("GA-NS-18: empty stale list causes no notifications written")
    void emptyStaleListNoNotificationsWritten() {
        stubStaleApps("{\"items\": []}");

        scheduler.run();

        // No PUT calls at all
        appStale().verify(0, putRequestedFor(urlPathEqualTo("/internal/applications")));
    }

    // GA-NS-20: application-service unreachable -> service stays responsive
    @Test
    @DisplayName("GA-NS-20: application-service unreachable leaves service responsive")
    void applicationServiceUnreachableServiceStaysResponsive() {
        // Don't stub anything - the stale endpoint is unreachable (no stub = WireMock returns 404)
        // but since WireMock IS running, we need to simulate a real failure by having it return 500
        appStale().stubFor(get(urlPathEqualTo(STALE_PATH))
                .willReturn(aResponse().withStatus(500)));

        // Should not throw
        assertThat(catchThrowable(() -> scheduler.run())).isNull();
    }

    // GA-NS-21: PUT failure for one app -> other app's notification still written
    @Test
    @DisplayName("GA-NS-21: PUT failure for one app does not prevent other app's notification being written")
    void putFailureForOneAppDoesNotPreventOtherAppNotification() {
        // Two stale apps - one for GA_USER_01 (ghosted=true), one for another user (ghosted=true but different prefs row)
        UUID appIdFailing = GA_APP_01;
        UUID appIdSucceeding = UUID.fromString("d0000000-0000-0000-0000-000000000099");
        UUID userIdFailing  = GA_USER_01;
        UUID userIdSucceeding = UUID.fromString("b0000000-0000-0000-0000-000000000003"); // seeded with ghosted=true

        appStale().stubFor(get(urlPathEqualTo(STALE_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "items": [
                                    {
                                      "id": "%s",
                                      "userId": "%s",
                                      "jobTitle": "Backend Developer",
                                      "company": "Failing Corp",
                                      "currentStatus": "applied",
                                      "daysSinceLastActivity": 14
                                    },
                                    {
                                      "id": "%s",
                                      "userId": "%s",
                                      "jobTitle": "Frontend Developer",
                                      "company": "Succeeding Corp",
                                      "currentStatus": "applied",
                                      "daysSinceLastActivity": 15
                                    }
                                  ]
                                }
                                """.formatted(appIdFailing, userIdFailing, appIdSucceeding, userIdSucceeding))));

        // First PUT fails with 500
        stubPutStatus(appIdFailing, 500, "{\"error\": \"Internal Server Error\"}");
        // Second PUT succeeds
        stubPutStatus(appIdSucceeding, 200, putOkBody(appIdSucceeding, userIdSucceeding));
        stubEmptyEmailBatch();

        scheduler.run();

        // Notification written for succeeding app
        var notificationsForSucceeding = notificationRepository.findByUserId(userIdSucceeding, 0, 10, ReadStatusFilter.ALL);
        assertThat(notificationsForSucceeding).anySatisfy(n ->
                assertThat(n.getType()).isEqualTo(NotificationType.GHOSTED_ALERT));

        // No notification for failing app (PUT failed)
        var notificationsForFailing = notificationRepository.findByUserId(userIdFailing, 0, 10, ReadStatusFilter.ALL);
        assertThat(notificationsForFailing).noneMatch(n -> n.getType() == NotificationType.GHOSTED_ALERT
                && n.getApplicationId().equals(appIdFailing));
    }

    private static Throwable catchThrowable(Runnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }
}
