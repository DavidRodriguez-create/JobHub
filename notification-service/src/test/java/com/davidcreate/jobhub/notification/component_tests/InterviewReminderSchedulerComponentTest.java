package com.davidcreate.jobhub.notification.component_tests;

import com.davidcreate.jobhub.notification.adapter.in.scheduler.InterviewReminderScheduler;
import com.davidcreate.jobhub.notification.component_tests.support.WireMockAppInternalResource;
import com.davidcreate.jobhub.notification.component_tests.support.WireMockAuthInternalResource;
import com.davidcreate.jobhub.notification.domain.model.ReminderOffset;
import com.davidcreate.jobhub.notification.domain.model.UpcomingNextStep;
import com.davidcreate.jobhub.notification.domain.port.out.InterviewReminderMailer;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end component tests for {@link InterviewReminderScheduler}, covering TC-121..TC-133
 * (Story #81, AC-1..AC-19). Uses DevServices real Postgres (interview_reminder_sent +
 * notification_preferences seeded via test-seeds.sql) and WireMock stubs for
 * application-service (upcoming-next-steps) and auth-service (email batch).
 * InterviewReminderMailer is mocked via @InjectMock.
 *
 * <p>Step dates are computed with {@link ZoneOffset#UTC} (not the JVM default zone) because
 * {@code InterviewReminderService} reckons H24/H1 fire instants in UTC. Using the local zone
 * would flake between local midnight and UTC midnight, when "tomorrow" locally is still today
 * in UTC and the H24 fire instant has not yet been reached.
 */
@QuarkusTest
@QuarkusTestResource(WireMockAuthInternalResource.class)
@QuarkusTestResource(WireMockAppInternalResource.class)
@DisplayName("InterviewReminderScheduler Component Tests")
class InterviewReminderSchedulerComponentTest {

    // Interview-reminder user IDs from test-seeds.sql (dd000000-... prefix, hex-valid,
    // distinct from b0/c0/e0 prefixes used by other test classes).
    private static final UUID J0001 = UUID.fromString("dd000000-0000-0000-0000-000000000001");
    private static final UUID J0002 = UUID.fromString("dd000000-0000-0000-0000-000000000002");
    private static final UUID J0003 = UUID.fromString("dd000000-0000-0000-0000-000000000003");
    private static final UUID J0004 = UUID.fromString("dd000000-0000-0000-0000-000000000004");
    private static final UUID J0005 = UUID.fromString("dd000000-0000-0000-0000-000000000005");
    private static final UUID J0006 = UUID.fromString("dd000000-0000-0000-0000-000000000006");
    private static final UUID J0007 = UUID.fromString("dd000000-0000-0000-0000-000000000007");
    private static final UUID J0008 = UUID.fromString("dd000000-0000-0000-0000-000000000008");
    private static final UUID J0009 = UUID.fromString("dd000000-0000-0000-0000-000000000009");
    private static final UUID J0010 = UUID.fromString("dd000000-0000-0000-0000-000000000010");
    private static final UUID J0011 = UUID.fromString("dd000000-0000-0000-0000-000000000011");
    private static final UUID J0012 = UUID.fromString("dd000000-0000-0000-0000-000000000012");

    // #153 regression users (ee110000-... prefix, see test-seeds.sql)
    private static final UUID J153_EMAIL_ON = UUID.fromString("ee110000-0000-0000-0000-000000000001");
    private static final UUID J153_EMAIL_OFF = UUID.fromString("ee110000-0000-0000-0000-000000000002");

    // Application IDs matched to seed data (da000000-... prefix)
    private static final UUID APP_0001 = UUID.fromString("da000000-0000-0000-0000-000000000001");
    private static final UUID APP_0002 = UUID.fromString("da000000-0000-0000-0000-000000000002");
    private static final UUID APP_0003 = UUID.fromString("da000000-0000-0000-0000-000000000003");
    private static final UUID APP_0004 = UUID.fromString("da000000-0000-0000-0000-000000000004");
    private static final UUID APP_0005 = UUID.fromString("da000000-0000-0000-0000-000000000005");
    private static final UUID APP_0006 = UUID.fromString("da000000-0000-0000-0000-000000000006");
    private static final UUID APP_0007 = UUID.fromString("da000000-0000-0000-0000-000000000007");
    private static final UUID APP_0009 = UUID.fromString("da000000-0000-0000-0000-000000000009");
    private static final UUID APP_0010 = UUID.fromString("da000000-0000-0000-0000-000000000010");
    private static final UUID APP_0011 = UUID.fromString("da000000-0000-0000-0000-000000000011");
    private static final UUID APP_0012 = UUID.fromString("da000000-0000-0000-0000-000000000012");

    // #153 regression applications (arbitrary fresh UUIDs, not seeded; WireMock-only)
    private static final UUID APP_153_EMAIL_ON = UUID.fromString("ee120000-0000-0000-0000-000000000001");
    private static final UUID APP_153_EMAIL_OFF = UUID.fromString("ee120000-0000-0000-0000-000000000002");

    private static final String UPCOMING_PATH = "/internal/applications/upcoming-next-steps";
    private static final String EMAILS_PATH = "/auth/internal/users/emails";

    @Inject
    InterviewReminderScheduler scheduler;

    @Inject
    EntityManager em;

    @InjectMock
    InterviewReminderMailer reminderMailer;

    @InjectMock
    Clock clock;

    @BeforeEach
    void resetWireMock() {
        authInternal().resetAll();
        appInternal().resetAll();
        // Pin "now" to noon UTC of the current UTC day. The H24/H1 fire instants are computed
        // from the day-granular nextStepDate (start-of-day UTC minus 24h / 1h), so the real
        // wall-clock time would otherwise flake near midnight: a "tomorrow" step's H1 instant
        // (tomorrow 00:00 - 1h = today 23:00) is reached in the last hour of the UTC day, and a
        // "tomorrow" step's H24 instant slips out of reach just after midnight. Noon is safely
        // inside both windows for "tomorrow" (H24 only) and "today" (H1) steps.
        when(clock.instant()).thenReturn(
                LocalDate.now(ZoneOffset.UTC).atTime(12, 0).toInstant(ZoneOffset.UTC));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @AfterEach
    @Transactional
    void cleanUp() {
        // Remove INTERVIEW_REMINDER notifications created by this test run for c-users
        String inClause = "'" + J0001 + "','" + J0002 + "','" + J0003 + "','" + J0004 + "','"
                + J0005 + "','" + J0006 + "','" + J0007 + "','" + J0008 + "','"
                + J0009 + "','" + J0010 + "','" + J0011 + "','" + J0012 + "'";
        em.createNativeQuery("DELETE FROM notification.notifications WHERE user_id IN (" + inClause + ") AND type = 'INTERVIEW_REMINDER'")
                .executeUpdate();
        // Remove H1 rows only (seeded H24 rows for c0002/c0005/c0008/c0009 remain)
        em.createNativeQuery("DELETE FROM notification.interview_reminder_sent WHERE user_id IN (" + inClause + ") AND reminder_offset = 'H1'")
                .executeUpdate();
        // Clean newly created H24 rows for users who had none seeded
        em.createNativeQuery("DELETE FROM notification.interview_reminder_sent WHERE user_id IN ('"
                + J0001 + "','" + J0003 + "','" + J0004 + "','" + J0006 + "','"
                + J0007 + "','" + J0010 + "','" + J0011 + "','" + J0012 + "') AND reminder_offset = 'H24'")
                .executeUpdate();

        // #153 regression cleanup
        em.createNativeQuery("DELETE FROM notification.notifications WHERE user_id IN ('"
                + J153_EMAIL_ON + "','" + J153_EMAIL_OFF + "') AND type = 'INTERVIEW_REMINDER'")
                .executeUpdate();
        em.createNativeQuery("DELETE FROM notification.interview_reminder_sent WHERE user_id IN ('"
                + J153_EMAIL_ON + "','" + J153_EMAIL_OFF + "')")
                .executeUpdate();
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

    private void stubUpcomingItems(String... itemsJson) {
        StringBuilder body = new StringBuilder("{\"items\":[");
        for (int i = 0; i < itemsJson.length; i++) {
            if (i > 0) body.append(",");
            body.append(itemsJson[i]);
        }
        body.append("]}");
        appInternal().stubFor(get(urlPathEqualTo(UPCOMING_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body.toString())));
    }

    private String item(UUID userId, UUID applicationId, String label,
                        LocalDate stepDate, String companyName) {
        String company = companyName == null ? "null" : "\"" + companyName + "\"";
        return """
                {"userId":"%s","applicationId":"%s","nextStepLabel":"%s",
                 "nextStepDate":"%s","companyName":%s,"status":"interviewing"}
                """.formatted(userId, applicationId, label, stepDate, company);
    }

    private long countNotifications(UUID userId) {
        return (long) em.createNativeQuery(
                "SELECT COUNT(*) FROM notification.notifications WHERE user_id = '" + userId + "' AND type = 'INTERVIEW_REMINDER'")
                .getSingleResult();
    }

    private long countReminderSent(UUID userId, String offset) {
        return (long) em.createNativeQuery(
                "SELECT COUNT(*) FROM notification.interview_reminder_sent WHERE user_id = '" + userId + "' AND reminder_offset = '" + offset + "'")
                .getSingleResult();
    }

    private String getChannels(UUID userId, String offset) {
        Object result = em.createNativeQuery(
                "SELECT channels FROM notification.interview_reminder_sent WHERE user_id = '" + userId + "' AND reminder_offset = '" + offset + "'")
                .getSingleResult();
        return result != null ? result.toString() : null;
    }

    // TC-121: in-app + email created for H24-due user with both channels on
    @Test
    @DisplayName("TC-121: scheduler_run_creates_in_app_and_email_for_h24_due_user")
    void schedulerRunCreatesInAppAndEmailForH24DueUser() {
        // j0001: interview_reminders=true, interview_reminder_email=true, no prior sent row
        // nextStepDate = tomorrow -> H24 fire instant reached (start-of-tomorrow - 24h = now)
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        stubUpcomingItems(item(J0001, APP_0001, "Interview with Product Manager", tomorrow, "Acme Corp"));
        stubEmailBatch(emailEntry(J0001, "j0001@example.com"));

        scheduler.run();

        assertThat(countNotifications(J0001)).isEqualTo(1);
        verify(reminderMailer, times(1)).send(eq("j0001@example.com"), any(UpcomingNextStep.class), eq(ReminderOffset.H24));
        assertThat(countReminderSent(J0001, "H24")).isEqualTo(1);
        assertThat(getChannels(J0001, "H24")).contains("in_app").contains("email");
    }

    // TC-127: notification content includes label, date, and company
    @Test
    @DisplayName("TC-127: scheduler_run_in_app_notification_content_includes_label_date_company")
    void schedulerRunInAppNotificationContentIncludesLabelDateCompany() {
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        stubUpcomingItems(item(J0001, APP_0001, "Interview with Product Manager", tomorrow, "Acme Corp"));
        stubEmailBatch(emailEntry(J0001, "j0001@example.com"));

        scheduler.run();

        Object[] row = (Object[]) em.createNativeQuery(
                "SELECT title, message FROM notification.notifications WHERE user_id = '" + J0001 + "' AND type = 'INTERVIEW_REMINDER'")
                .getSingleResult();
        String combined = row[0] + " " + row[1];
        assertThat(combined).contains("Interview with Product Manager");
        assertThat(combined).contains(tomorrow.toString());
        assertThat(combined).contains("Acme Corp");
    }

    // IR-C-39: H24 and H1 round-trip persist the writer's exact title/message and keep
    // applicationId set (icon-rendering signal for INTERVIEW_REMINDER)
    @Test
    @Transactional
    @DisplayName("IR-C-39: H24 and H1 round-trips persist exact playful copy and keep applicationId set")
    void h24AndH1RoundTripsPersistExactCopyAndKeepApplicationIdSet() {
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        stubUpcomingItems(item(J0006, APP_0006, "Onsite interview", tomorrow, "Acme Corp"));
        stubEmailBatch(emailEntry(J0006, "j0006@example.com"));

        scheduler.run();

        Object[] h24Row = (Object[]) em.createNativeQuery(
                "SELECT title, message, application_id FROM notification.notifications "
                        + "WHERE user_id = '" + J0006 + "' AND type = 'INTERVIEW_REMINDER'")
                .getSingleResult();
        assertThat(h24Row[0]).isEqualTo("⏰ Countdown to showtime!");
        assertThat(h24Row[1]).isEqualTo(
                "Your Onsite interview with Acme Corp is coming up in about 24 hours (" + tomorrow
                        + "). Take a breath, review your notes, and go show them what you've got.");
        assertThat(h24Row[2]).isNotNull();
        assertThat(UUID.fromString(h24Row[2].toString())).isEqualTo(APP_0006);

        // H1 round-trip for a separate user/today date
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        em.createNativeQuery("DELETE FROM notification.notifications WHERE user_id = '" + J0006 + "'").executeUpdate();
        stubUpcomingItems(item(J0006, APP_0006, "Onsite interview", today, "Acme Corp"));
        em.createNativeQuery("DELETE FROM notification.interview_reminder_sent WHERE user_id = '" + J0006 + "' AND reminder_offset = 'H24'").executeUpdate();
        em.createNativeQuery(
                "INSERT INTO notification.interview_reminder_sent (id, user_id, application_id, reminder_offset, next_step_date, channels, sent_at) "
                        + "VALUES (gen_random_uuid(), '" + J0006 + "', '" + APP_0006 + "', 'H24', '" + today + "', 'in_app', now())")
                .executeUpdate();

        scheduler.run();

        Object[] h1Row = (Object[]) em.createNativeQuery(
                "SELECT title, message, application_id FROM notification.notifications "
                        + "WHERE user_id = '" + J0006 + "' AND type = 'INTERVIEW_REMINDER'")
                .getSingleResult();
        assertThat(h1Row[0]).isEqualTo("🎤 You're up soon!");
        assertThat(h1Row[1]).isEqualTo(
                "Your Onsite interview with Acme Corp kicks off in about 1 hour (" + today
                        + "). Grab some water, take a breath, you've got this.");
        assertThat(h1Row[2]).isNotNull();
        assertThat(UUID.fromString(h1Row[2].toString())).isEqualTo(APP_0006);
    }

    // TC-122: H1 fires independently when H24 already sent (j0002 has H24 seed row)
    @Test
    @DisplayName("TC-122: scheduler_run_fires_h1_independently_when_h24_already_sent")
    void schedulerRunFiresH1IndependentlyWhenH24AlreadySent() {
        // j0002: H24 already in interview_reminder_sent (seeded); nextStepDate = today -> H1 fires
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        stubUpcomingItems(item(J0002, APP_0002, "Onsite interview", today, "Acme Corp"));
        stubEmailBatch(emailEntry(J0002, "j0002@example.com"));

        scheduler.run();

        assertThat(countReminderSent(J0002, "H1")).isEqualTo(1);
        // Total: 1 seeded H24 + 1 new H1
        long total = (long) em.createNativeQuery(
                "SELECT COUNT(*) FROM notification.interview_reminder_sent WHERE user_id = '" + J0002 + "'")
                .getSingleResult();
        assertThat(total).isEqualTo(2);
    }

    // TC-123: in-app created but no email for j0004 (interview_reminder_email=false)
    @Test
    @DisplayName("TC-123: scheduler_run_does_not_email_when_interviewReminderEmail_false")
    void schedulerRunDoesNotEmailWhenInterviewReminderEmailFalse() {
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        stubUpcomingItems(item(J0004, APP_0004, "Phone screen", tomorrow, "Globex"));
        stubEmailBatch(); // no emails returned

        scheduler.run();

        assertThat(countNotifications(J0004)).isEqualTo(1);
        verify(reminderMailer, never()).send(anyString(), any(), any());
        assertThat(countReminderSent(J0004, "H24")).isEqualTo(1);
    }

    // TC-126: channels='in_app' for email-off user
    @Test
    @DisplayName("TC-126: scheduler_run_in_app_only_channels_value_for_email_off_user")
    void schedulerRunInAppOnlyChannelsValueForEmailOffUser() {
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        stubUpcomingItems(item(J0004, APP_0004, "Phone screen", tomorrow, "Globex"));
        stubEmailBatch();

        scheduler.run();

        assertThat(getChannels(J0004, "H24")).isEqualTo("in_app");
    }

    // TC-124: H24 not resent on re-tick for j0005 (already has H24 row seeded)
    @Test
    @DisplayName("TC-124: scheduler_run_does_not_resend_already_sent_h24_on_retick")
    void schedulerRunDoesNotResendAlreadySentH24OnRetick() {
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        // j0005: H24 seed row for APP_0005/tomorrow; same item returned again
        stubUpcomingItems(item(J0005, APP_0005, "Final interview", tomorrow, "Initech"));
        stubEmailBatch(emailEntry(J0005, "j0005@example.com"));

        scheduler.run();

        // H24 count stays at 1 (the seeded one)
        assertThat(countReminderSent(J0005, "H24")).isEqualTo(1);
        verify(reminderMailer, never()).send(eq("j0005@example.com"), any(), eq(ReminderOffset.H24));
    }

    // TC-125: master switch off -> nothing written for j0003
    @Test
    @DisplayName("TC-125: scheduler_run_skips_user_with_master_switch_off_writes_no_row")
    void schedulerRunSkipsUserWithMasterSwitchOffWritesNoRow() {
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        stubUpcomingItems(item(J0003, APP_0003, "HR call", tomorrow, "Umbrella"));
        stubEmailBatch(emailEntry(J0003, "j0003@example.com"));

        scheduler.run();

        assertThat(countNotifications(J0003)).isEqualTo(0);
        verify(reminderMailer, never()).send(anyString(), any(), any());
        long totalSent = (long) em.createNativeQuery(
                "SELECT COUNT(*) FROM notification.interview_reminder_sent WHERE user_id = '" + J0003 + "'")
                .getSingleResult();
        assertThat(totalSent).isEqualTo(0);
    }

    // TC-128: no-op when upcoming items are empty
    @Test
    @DisplayName("TC-128: scheduler_run_no_op_when_internal_endpoint_returns_empty_items")
    void schedulerRunNoOpWhenInternalEndpointReturnsEmptyItems() {
        stubUpcomingItems(); // empty
        authInternal().stubFor(get(urlPathEqualTo(EMAILS_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"emails\":[]}")));

        assertDoesNotThrow(() -> scheduler.run());

        verify(reminderMailer, never()).send(any(), any(), any());
    }

    // TC-129: null company does not produce "null" in content
    @Test
    @DisplayName("TC-129: scheduler_run_records_sent_row_for_item_with_null_company_name")
    void schedulerRunRecordsSentRowForItemWithNullCompanyName() {
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        stubUpcomingItems(item(J0007, APP_0007, "Technical interview", tomorrow, null));
        stubEmailBatch(emailEntry(J0007, "j0007@example.com"));

        scheduler.run();

        assertThat(countReminderSent(J0007, "H24")).isEqualTo(1);
        assertThat(countNotifications(J0007)).isEqualTo(1);

        Object[] row = (Object[]) em.createNativeQuery(
                "SELECT title, message FROM notification.notifications WHERE user_id = '" + J0007 + "' AND type = 'INTERVIEW_REMINDER'")
                .getSingleResult();
        String combined = (row[0] + " " + row[1]).toLowerCase();
        assertThat(combined).doesNotContain("null");
        assertThat(combined).doesNotContain("undefined");

        ArgumentCaptor<UpcomingNextStep> stepCaptor = ArgumentCaptor.forClass(UpcomingNextStep.class);
        verify(reminderMailer, times(1)).send(anyString(), stepCaptor.capture(), any());
        // company is null on the step passed to mailer
        assertThat(stepCaptor.getValue().getCompany()).isNull();
    }

    // TC-130: H1 not created when item disappears (AC-16 mid-flight terminalisation)
    @Test
    @DisplayName("TC-130: scheduler_run_h1_not_created_when_item_no_longer_in_response")
    void schedulerRunH1NotCreatedWhenItemNoLongerInResponse() {
        // j0008 has H24 seed row; upcoming does NOT include j0008 this tick
        stubUpcomingItems(); // empty response
        authInternal().stubFor(get(urlPathEqualTo(EMAILS_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"emails\":[]}")));

        scheduler.run();

        assertThat(countReminderSent(J0008, "H1")).isEqualTo(0);
        assertThat(countNotifications(J0008)).isEqualTo(0);
    }

    // TC-131: reschedule -> H24 not re-armed, H1 fires for new date (AC-17)
    @Test
    @DisplayName("TC-131: scheduler_run_h24_not_rearmed_after_reschedule_but_h1_fires_for_new_date")
    void schedulerRunH24NotRearmedAfterRescheduleButH1FiresForNewDate() {
        // j0009 has H24 seed row (for old date); new item has today's date -> H1 fire instant reached
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        stubUpcomingItems(item(J0009, APP_0009, "Final round", today, "Acme"));
        stubEmailBatch(emailEntry(J0009, "j0009@example.com"));

        scheduler.run();

        // H24 count stays at 1 (the seeded one, not re-inserted)
        assertThat(countReminderSent(J0009, "H24")).isEqualTo(1);
        // H1 fires
        assertThat(countReminderSent(J0009, "H1")).isEqualTo(1);
        assertThat(countNotifications(J0009)).isEqualTo(1);
    }

    // TC-132 + TC-133 / TC-209-CM-07 (AC-7): partial failure isolation, now resolving the
    // previously-noncommittal post-failure state: in-app stands, channels="in_app" only, the
    // sent row is written exactly once and a later retry (mailer no longer throwing) does NOT
    // create a second sent row or a second notification (permanently-missed email, by design).
    @Test
    @DisplayName("TC-132/TC-133/TC-209-CM-07 (AC-7): scheduler_run_isolates_failure_of_one_combination_from_another")
    void schedulerRunIsolatesFailureOfOneCombinationFromAnother() {
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);

        // X = j0010 (H24 due), Y = j0011 (H24 due): process both in same tick
        stubUpcomingItems(
                item(J0010, APP_0010, "Interview X", tomorrow, "FailCo"),
                item(J0011, APP_0011, "Interview Y", tomorrow, "GoodCo"));

        // Both have emails
        stubEmailBatch(
                emailEntry(J0010, "j0010@example.com"),
                emailEntry(J0011, "j0011@example.com"));

        // Mailer throws for J0010, succeeds for J0011
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP failure for X"))
                .when(reminderMailer).send(eq("j0010@example.com"), any(), any());

        assertDoesNotThrow(() -> scheduler.run());

        // Y processed normally: notification + sent row, channels include email
        assertThat(countNotifications(J0011)).isEqualTo(1);
        assertThat(countReminderSent(J0011, "H24")).isEqualTo(1);
        verify(reminderMailer, times(1)).send(eq("j0011@example.com"), any(), any());
        assertThat(getChannels(J0011, "H24")).contains("in_app").contains("email");

        // X: in-app notification stands (saved before the mailer threw); sent row IS written
        // exactly once, with channels="in_app" only (AC-7 resolves the prior ambiguity).
        assertThat(countNotifications(J0010)).isEqualTo(1);
        assertThat(countReminderSent(J0010, "H24")).isEqualTo(1);
        assertThat(getChannels(J0010, "H24")).isEqualTo("in_app");

        // TC-133: a later retick (mailer no longer throwing for X) does not create a second
        // sent row or a second notification for X - the offset was already recorded as
        // processed, so the missed email is permanent for this offset (accepted, not a defect).
        org.mockito.Mockito.clearInvocations(reminderMailer);
        org.mockito.Mockito.doNothing().when(reminderMailer).send(anyString(), any(), any());

        assertDoesNotThrow(() -> scheduler.run());

        assertThat(countNotifications(J0010)).isEqualTo(1);
        assertThat(countReminderSent(J0010, "H24")).isEqualTo(1);
        verify(reminderMailer, never()).send(eq("j0010@example.com"), any(), any());
    }

    // CR-153-C-010: email IS sent when interviewReminders=true AND interviewReminderEmail=true
    @Test
    @DisplayName("CR-153-C-010: scheduler_sends_email_when_both_prefs_toggles_are_on")
    void cr153c010SchedulerSendsEmailWhenBothPrefsToggleAreOn() {
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        stubUpcomingItems(item(J153_EMAIL_ON, APP_153_EMAIL_ON, "Interview", tomorrow, "Acme"));
        stubEmailBatch(emailEntry(J153_EMAIL_ON, "ee110001@example.com"));

        scheduler.run();

        verify(reminderMailer, times(1)).send(eq("ee110001@example.com"), any(UpcomingNextStep.class), eq(ReminderOffset.H24));
        assertThat(getChannels(J153_EMAIL_ON, "H24")).contains("email");
    }

    // CR-153-C-011: email is NOT sent when interviewReminderEmail=false; in-app still written
    @Test
    @DisplayName("CR-153-C-011: scheduler_does_not_send_email_when_interviewReminderEmail_is_off")
    void cr153c011SchedulerDoesNotSendEmailWhenInterviewReminderEmailIsOff() {
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        stubUpcomingItems(item(J153_EMAIL_OFF, APP_153_EMAIL_OFF, "Interview", tomorrow, "Acme"));
        stubEmailBatch(emailEntry(J153_EMAIL_OFF, "ee110002@example.com"));

        scheduler.run();

        verify(reminderMailer, never()).send(eq("ee110002@example.com"), any(), any());
        assertThat(countNotifications(J153_EMAIL_OFF)).isEqualTo(1);
        assertThat(getChannels(J153_EMAIL_OFF, "H24")).isEqualTo("in_app");
    }

    // TC-209-C-03 (AC-3): weeklyDigestEmail=false toggle-independence regression - the interview
    // email still sends normally through the real scheduler/WireMock path when the digest
    // preference is off, proving no code path in the scheduler reads weeklyDigestEmail.
    @Test
    @DisplayName("TC-209-C-03 (AC-3): scheduler_sends_interview_email_when_weeklyDigestEmail_is_false")
    void schedulerSendsInterviewEmailWhenWeeklyDigestEmailIsFalse() {
        // j0012: weekly_digest_email=false, interview_reminders=true, interview_reminder_email=true
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        stubUpcomingItems(item(J0012, APP_0012, "Interview with Product Manager", tomorrow, "Acme Corp"));
        stubEmailBatch(emailEntry(J0012, "j0012@example.com"));

        scheduler.run();

        assertThat(countNotifications(J0012)).isEqualTo(1);
        verify(reminderMailer, times(1)).send(eq("j0012@example.com"), any(UpcomingNextStep.class), eq(ReminderOffset.H24));
        assertThat(countReminderSent(J0012, "H24")).isEqualTo(1);
        assertThat(getChannels(J0012, "H24")).contains("email");
    }

    // TC-209-C-08-NEW (AC-8): auth-service unreachable while resolving emails (WireMock fault) -
    // in-app still fires for the H24-due user, no email is sent, the run does not crash, and the
    // sent row is written with channels="in_app" only.
    @Test
    @DisplayName("TC-209-C-08-NEW (AC-8): scheduler_run_degrades_to_in_app_only_when_auth_service_unreachable")
    void schedulerRunDegradesToInAppOnlyWhenAuthServiceUnreachable() {
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        stubUpcomingItems(item(J0001, APP_0001, "Interview with Product Manager", tomorrow, "Acme Corp"));

        authInternal().stubFor(get(urlPathEqualTo(EMAILS_PATH))
                .willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        assertDoesNotThrow(() -> scheduler.run());

        assertThat(countNotifications(J0001)).isEqualTo(1);
        verify(reminderMailer, never()).send(any(), any(), any());
        assertThat(countReminderSent(J0001, "H24")).isEqualTo(1);
        assertThat(getChannels(J0001, "H24")).isEqualTo("in_app");
    }
}
