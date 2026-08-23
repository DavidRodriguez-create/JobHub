package com.davidcreate.jobhub.notification.component_tests;

import com.davidcreate.jobhub.notification.adapter.in.scheduler.CustomReminderDispatchScheduler;
import com.davidcreate.jobhub.notification.component_tests.support.CustomReminderEnabledProfile;
import com.davidcreate.jobhub.notification.component_tests.support.WireMockAppInternalResource;
import com.davidcreate.jobhub.notification.component_tests.support.WireMockAuthInternalResource;
import com.davidcreate.jobhub.notification.domain.port.out.CustomReminderMailer;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@QuarkusTest
@TestProfile(CustomReminderEnabledProfile.class)
@QuarkusTestResource(WireMockAuthInternalResource.class)
@QuarkusTestResource(WireMockAppInternalResource.class)
@DisplayName("CustomReminderDispatchScheduler Component Tests")
class CustomReminderDispatchSchedulerComponentTest {

    private static final UUID USER_10 = UUID.fromString("ee000000-0000-0000-0000-000000000010");
    private static final UUID USER_11 = UUID.fromString("ee000000-0000-0000-0000-000000000011");
    private static final UUID USER_12 = UUID.fromString("ee000000-0000-0000-0000-000000000012");
    private static final UUID USER_13 = UUID.fromString("ee000000-0000-0000-0000-000000000013");

    private static final UUID REMINDER_10 = UUID.fromString("ec000000-0000-0000-0000-000000000010");
    private static final UUID REMINDER_11 = UUID.fromString("ec000000-0000-0000-0000-000000000011");
    private static final UUID REMINDER_12 = UUID.fromString("ec000000-0000-0000-0000-000000000012");
    private static final UUID REMINDER_13 = UUID.fromString("ec000000-0000-0000-0000-000000000013");
    private static final UUID REMINDER_14 = UUID.fromString("ec000000-0000-0000-0000-000000000014");
    private static final UUID REMINDER_15 = UUID.fromString("ec000000-0000-0000-0000-000000000015");

    private static final String EMAILS_PATH = "/auth/internal/users/emails";

    @Inject
    CustomReminderDispatchScheduler scheduler;

    @Inject
    EntityManager em;

    @InjectMock
    CustomReminderMailer mailer;

    @BeforeEach
    void resetWireMockAndMocks() {
        authInternal().resetAll();
        appInternal().resetAll();
        // Clear any invocations that may have accumulated from a prior test or an auto-fire of
        // the @Scheduled cron before the scheduler engine was confirmed disabled. This is defence
        // in depth: the root-cause fix is quarkus.scheduler.enabled=false in the profile.
        clearInvocations(mailer);
    }

    private WireMockServer authInternal() {
        return WireMockAuthInternalResource.server();
    }

    private WireMockServer appInternal() {
        return WireMockAppInternalResource.server();
    }

    private void stubEmailBatch(String... entries) {
        StringBuilder body = new StringBuilder("{\"emails\":[");
        for (int i = 0; i < entries.length; i++) {
            if (i > 0) body.append(",");
            body.append(entries[i]);
        }
        body.append("]}");
        authInternal().stubFor(get(urlPathEqualTo(EMAILS_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body.toString())));
    }

    private String emailEntry(UUID userId, String email) {
        return "{\"userId\":\"" + userId + "\",\"email\":\"" + email + "\"}";
    }

    private String status(UUID reminderId) {
        return (String) em.createNativeQuery(
                "SELECT status FROM notification.custom_reminder WHERE id = '" + reminderId + "'")
                .getSingleResult();
    }

    private String channelsFired(UUID reminderId) {
        Object result = em.createNativeQuery(
                "SELECT channels_fired FROM notification.custom_reminder WHERE id = '" + reminderId + "'")
                .getSingleResult();
        return result != null ? result.toString() : null;
    }

    private long countNotifications(UUID userId) {
        return (long) em.createNativeQuery(
                "SELECT COUNT(*) FROM notification.notifications WHERE user_id = '" + userId + "' AND type = 'CUSTOM_REMINDER'")
                .getSingleResult();
    }

    @org.junit.jupiter.api.AfterEach
    @Transactional
    void cleanUp() {
        em.createNativeQuery("DELETE FROM notification.notifications WHERE type = 'CUSTOM_REMINDER' AND user_id IN ('"
                + USER_10 + "','" + USER_11 + "','" + USER_12 + "','" + USER_13 + "')")
                .executeUpdate();
        em.createNativeQuery("UPDATE notification.custom_reminder SET status = 'SCHEDULED', channels_fired = NULL, fired_at_utc = NULL "
                + "WHERE id IN ('" + REMINDER_10 + "','" + REMINDER_11 + "','" + REMINDER_12 + "','" + REMINDER_13 + "','" + REMINDER_15 + "')")
                .executeUpdate();
    }

    // CR-C-070
    @Test
    @DisplayName("CR-C-070: IN_APP + EMAIL dispatched when prefs allow both")
    void inAppAndEmailDispatchedWhenPrefsAllowBoth() {
        stubEmailBatch(emailEntry(USER_10, "ee000010@example.com"));

        scheduler.run();

        verify(mailer, times(1)).send(eq("ee000010@example.com"), any());
        assertThat(countNotifications(USER_10)).isGreaterThanOrEqualTo(1);
        assertThat(status(REMINDER_10)).isEqualTo("FIRED");
        assertThat(channelsFired(REMINDER_10)).contains("IN_APP").contains("EMAIL");
    }

    // CR-C-071
    @Test
    @DisplayName("CR-C-071: EMAIL gated when interviewReminderEmail=false; IN_APP delivered")
    void emailGatedWhenPrefsDisabled() {
        stubEmailBatch(emailEntry(USER_11, "ee000011@example.com"));

        scheduler.run();

        verify(mailer, never()).send(eq("ee000011@example.com"), any());
        assertThat(status(REMINDER_11)).isEqualTo("FIRED");
        assertThat(channelsFired(REMINDER_11)).isEqualTo("IN_APP");
    }

    // CR-C-072
    @Test
    @DisplayName("CR-C-072: email-only reminder gated to empty channels -> FIRED with empty channels_fired")
    void emailOnlyGatedToEmptyChannels() {
        stubEmailBatch(emailEntry(USER_11, "ee000011@example.com"));

        scheduler.run();

        verify(mailer, never()).send(anyString(), any());
        assertThat(status(REMINDER_15)).isEqualTo("FIRED");
        assertThat(channelsFired(REMINDER_15)).isEqualTo("");
    }

    // CR-C-073
    @Test
    @DisplayName("CR-C-073: no prefs row defaults to both channels on")
    void noPrefsRowDefaultsToBothOn() {
        stubEmailBatch(emailEntry(USER_12, "ee000012@example.com"));

        scheduler.run();

        verify(mailer, times(1)).send(eq("ee000012@example.com"), any());
        assertThat(countNotifications(USER_12)).isGreaterThanOrEqualTo(1);
        assertThat(status(REMINDER_12)).isEqualTo("FIRED");
        assertThat(channelsFired(REMINDER_12)).contains("IN_APP").contains("EMAIL");
    }

    // CR-C-074
    @Test
    @DisplayName("CR-C-074: IN_APP-only reminder does not call mailer")
    void inAppOnlyReminderDoesNotCallMailer() {
        stubEmailBatch();

        scheduler.run();

        verify(mailer, never()).send(anyString(), any());
        assertThat(status(REMINDER_13)).isEqualTo("FIRED");
        assertThat(channelsFired(REMINDER_13)).isEqualTo("IN_APP");
    }

    // CR-C-075
    @Test
    @DisplayName("CR-C-075: idempotent on already-FIRED reminder")
    void idempotentOnAlreadyFired() {
        stubEmailBatch();

        assertDoesNotThrow(() -> scheduler.run());

        assertThat(status(REMINDER_14)).isEqualTo("FIRED");
    }

    // CR-C-076
    @Test
    @DisplayName("CR-C-076: CANCELLED reminder not dispatched")
    void cancelledReminderNotDispatched() {
        stubEmailBatch();
        UUID cancelledId = UUID.fromString("ec000000-0000-0000-0000-000000000016");

        scheduler.run();

        assertThat(status(cancelledId)).isEqualTo("CANCELLED");
    }

    // CR-C-077
    @Test
    @DisplayName("CR-C-077: partial failure -- one mailer failure does not abort others")
    void partialFailureIsolation() {
        stubEmailBatch(emailEntry(USER_10, "ee000010@example.com"));
        org.mockito.Mockito.doThrow(new RuntimeException("smtp failure"))
                .when(mailer).send(eq("ee000010@example.com"), any());

        assertDoesNotThrow(() -> scheduler.run());

        // Other due reminders in the same batch (e.g. REMINDER_13, in-app only) are unaffected.
        assertThat(status(REMINDER_13)).isEqualTo("FIRED");
    }
}
