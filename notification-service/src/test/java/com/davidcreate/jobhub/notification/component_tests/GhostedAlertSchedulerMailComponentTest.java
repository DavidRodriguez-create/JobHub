package com.davidcreate.jobhub.notification.component_tests;

import com.davidcreate.jobhub.notification.adapter.in.scheduler.GhostedAlertScheduler;
import com.davidcreate.jobhub.notification.adapter.out.persistence.NotificationPanacheRepository;
import com.davidcreate.jobhub.notification.component_tests.support.WireMockAppStaleResource;
import com.davidcreate.jobhub.notification.component_tests.support.WireMockAuthInternalResource;
import com.davidcreate.jobhub.notification.domain.model.NotificationType;
import com.davidcreate.jobhub.notification.domain.model.ReadStatusFilter;
import com.davidcreate.jobhub.notification.domain.port.out.AlertMailer;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.InjectMock;
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
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * GA-NS-16: email sent for verified user.
 * Must be a separate @QuarkusTest class because @InjectMock replaces AlertMailer for the
 * whole Quarkus test application context — per CLAUDE.md rule.
 */
@QuarkusTest
@QuarkusTestResource(WireMockAppStaleResource.class)
@QuarkusTestResource(WireMockAuthInternalResource.class)
@DisplayName("GhostedAlertScheduler Component Tests - Email (InjectMock)")
class GhostedAlertSchedulerMailComponentTest {

    private static final UUID GA_USER_01 = UUID.fromString("b0000000-0000-0000-0000-000000000001");
    private static final UUID GA_APP_01  = UUID.fromString("d0000000-0000-0000-0000-000000000001");

    // Story #209, AC-6 cross-feature independence users (see test-seeds.sql, ee130000- prefix)
    private static final UUID AC6_GHOSTED_OFF_USER = UUID.fromString("ee130000-0000-0000-0000-000000000001");
    private static final UUID AC6_GHOSTED_OFF_APP = UUID.fromString("ee140000-0000-0000-0000-000000000001");
    private static final UUID AC6_GHOSTED_ON_USER = UUID.fromString("ee130000-0000-0000-0000-000000000002");
    private static final UUID AC6_GHOSTED_ON_APP = UUID.fromString("ee140000-0000-0000-0000-000000000002");

    private static final String STALE_PATH = "/internal/applications/stale";
    private static final String EMAILS_PATH = "/auth/internal/users/emails";

    @Inject
    GhostedAlertScheduler scheduler;

    @Inject
    NotificationPanacheRepository notificationRepository;

    @InjectMock
    AlertMailer alertMailer;

    @BeforeEach
    void resetWireMock() {
        appStale().resetAll();
        authInternal().resetAll();
    }

    @AfterEach
    @Transactional
    void cleanGeneratedNotifications() {
        // Remove only the ghosted-alert notifications this scheduler created for its own
        // test users, scoped so the #182 e0000000-...-0007 deep-link seed row (also
        // GHOSTED_ALERT with an applicationId) is never touched by this cleanup.
        com.davidcreate.jobhub.notification.adapter.out.persistence.entity.NotificationEntity
                .delete("type = ?1 and applicationId is not null and userId in (?2, ?3, ?4)",
                        NotificationType.GHOSTED_ALERT.name(), GA_USER_01,
                        AC6_GHOSTED_OFF_USER, AC6_GHOSTED_ON_USER);
    }

    private WireMockServer appStale() {
        return WireMockAppStaleResource.server();
    }

    private WireMockServer authInternal() {
        return WireMockAuthInternalResource.server();
    }

    // GA-NS-16: Email sent for verified user (separate @InjectMock class for Mailer)
    @Test
    @DisplayName("GA-NS-16: email sent for verified user (AlertMailer is mocked)")
    void emailSentForVerifiedUser() {
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
                                      "company": "Acme Corp",
                                      "currentStatus": "applied",
                                      "daysSinceLastActivity": 14
                                    }
                                  ]
                                }
                                """.formatted(GA_APP_01, GA_USER_01))));

        appStale().stubFor(put(urlEqualTo("/internal/applications/" + GA_APP_01 + "/status"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id": "%s", "userId": "%s", "newStatus": "ghosted"}
                                """.formatted(GA_APP_01, GA_USER_01))));

        // User has a verified email address
        authInternal().stubFor(get(urlPathEqualTo(EMAILS_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "emails": [
                                    {"userId": "%s", "email": "user@example.com"}
                                  ]
                                }
                                """.formatted(GA_USER_01))));

        scheduler.run();

        verify(alertMailer, times(1)).sendGhostedAlert(eq("user@example.com"), any());
    }

    // TC-209-CG-06a (AC-6): ghostedAlert=false, stacked with NON-default interview/digest
    // values (interviewReminders=false, interviewReminderEmail=true, weeklyDigestEmail=true) -
    // proves GhostedAlertService never reads those three fields. Per the frozen US5 behaviour
    // (docs/specs/US5-ghosted-alert.md line 80: "ghostedAlert=false: the user's stale
    // applications are entirely skipped during the run"), the status PUT is also never called
    // for this user - this is the pre-existing, already-tested contract (see GA-NS-17), not a
    // new claim. (Note: the PDA's #209 doc states the status transition is independent of the
    // preference; that does not match the frozen US5 behaviour or the existing GA-NS-17 test
    // and is flagged back to product/QA rather than silently changed here.)
    @Test
    @DisplayName("TC-209-CG-06a (AC-6): ghostedAlert=false suppresses status update and both channels regardless of non-default interview/digest prefs")
    void ghostedAlertFalseSuppressesBothChannelsRegardlessOfNonDefaultInterviewDigestPrefs() {
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
                                      "company": "Acme Corp",
                                      "currentStatus": "applied",
                                      "daysSinceLastActivity": 14
                                    }
                                  ]
                                }
                                """.formatted(AC6_GHOSTED_OFF_APP, AC6_GHOSTED_OFF_USER))));

        appStale().stubFor(put(urlEqualTo("/internal/applications/" + AC6_GHOSTED_OFF_APP + "/status"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id": "%s", "userId": "%s", "newStatus": "ghosted"}
                                """.formatted(AC6_GHOSTED_OFF_APP, AC6_GHOSTED_OFF_USER))));

        authInternal().stubFor(get(urlPathEqualTo(EMAILS_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"emails\": []}")));

        scheduler.run();

        // ghostedAlert=false skips the user entirely: no status PUT, no email.
        appStale().verify(0, com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor(
                urlEqualTo("/internal/applications/" + AC6_GHOSTED_OFF_APP + "/status")));
        verify(alertMailer, never()).sendGhostedAlert(any(), any());
    }

    // TC-209-CG-06b (AC-6, "conversely" half): ghostedAlert=true + interviewReminders=false -
    // disabling interview reminders has zero suppressive effect on the ghosted-alert feature;
    // ghosted email fires normally.
    @Test
    @DisplayName("TC-209-CG-06b (AC-6): ghostedAlert=true with interviewReminders=false still sends ghosted email normally")
    void ghostedAlertTrueWithInterviewRemindersFalseStillSendsGhostedEmailNormally() {
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
                                      "company": "Acme Corp",
                                      "currentStatus": "applied",
                                      "daysSinceLastActivity": 14
                                    }
                                  ]
                                }
                                """.formatted(AC6_GHOSTED_ON_APP, AC6_GHOSTED_ON_USER))));

        appStale().stubFor(put(urlEqualTo("/internal/applications/" + AC6_GHOSTED_ON_APP + "/status"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id": "%s", "userId": "%s", "newStatus": "ghosted"}
                                """.formatted(AC6_GHOSTED_ON_APP, AC6_GHOSTED_ON_USER))));

        authInternal().stubFor(get(urlPathEqualTo(EMAILS_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "emails": [
                                    {"userId": "%s", "email": "ghosted-on@example.com"}
                                  ]
                                }
                                """.formatted(AC6_GHOSTED_ON_USER))));

        scheduler.run();

        verify(alertMailer, times(1)).sendGhostedAlert(eq("ghosted-on@example.com"), any());

        // The in-app GHOSTED_ALERT notification must also be created for this user/app,
        // not just the email - interviewReminders=false has zero suppressive effect on
        // either ghosted-alert channel.
        var notifications = notificationRepository.findByUserId(AC6_GHOSTED_ON_USER, 0, 10, ReadStatusFilter.ALL);
        assertThat(notifications).anySatisfy(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.GHOSTED_ALERT);
            assertThat(n.getApplicationId()).isEqualTo(AC6_GHOSTED_ON_APP);
        });
    }
}
