package com.davidcreate.jobhub.notification.unit_tests.domain.service;

import com.davidcreate.jobhub.notification.domain.model.InterviewReminderSent;
import com.davidcreate.jobhub.notification.domain.model.Notification;
import com.davidcreate.jobhub.notification.domain.model.NotificationPreferences;
import com.davidcreate.jobhub.notification.domain.model.NotificationType;
import com.davidcreate.jobhub.notification.domain.model.ReminderOffset;
import com.davidcreate.jobhub.notification.domain.model.UpcomingNextStep;
import com.davidcreate.jobhub.notification.domain.port.out.InterviewReminderMailer;
import com.davidcreate.jobhub.notification.domain.port.out.InterviewReminderSentRepository;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationPreferencesRepository;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationRepository;
import com.davidcreate.jobhub.notification.domain.port.out.UpcomingNextStepsGateway;
import com.davidcreate.jobhub.notification.domain.port.out.UserEmailGateway;
import com.davidcreate.jobhub.notification.domain.service.InterviewReminderService;
import com.davidcreate.jobhub.notification.domain.service.NotificationCopyWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterviewReminderService Unit Tests")
class InterviewReminderServiceTest {

    @Mock UpcomingNextStepsGateway upcomingNextStepsGateway;
    @Mock NotificationPreferencesRepository preferencesRepository;
    @Mock InterviewReminderSentRepository reminderSentRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock UserEmailGateway userEmailGateway;
    @Mock InterviewReminderMailer reminderMailer;

    private static final UUID U1 = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID U2 = UUID.fromString("a1000000-0000-0000-0000-000000000002");
    private static final UUID A1 = UUID.fromString("b1000000-0000-0000-0000-000000000001");
    private static final UUID A2 = UUID.fromString("b1000000-0000-0000-0000-000000000002");

    // "now" is fixed to: 2026-06-17T01:00:00Z
    // H24 for 2026-06-18 fires at: 2026-06-18T00:00:00Z - 24h = 2026-06-17T00:00:00Z -> reached
    // H1  for 2026-06-17 fires at: 2026-06-17T00:00:00Z -  1h = 2026-06-16T23:00:00Z -> reached
    // H24 for 2026-06-17 fires at: 2026-06-17T00:00:00Z - 24h = 2026-06-16T00:00:00Z -> reached
    private static final Instant NOW = Instant.parse("2026-06-17T01:00:00Z");
    // tomorrow relative to NOW (for H24-only, H1 not reached)
    private static final LocalDate TOMORROW = LocalDate.of(2026, 6, 18);
    // today relative to NOW (for H1-reached and H24-reached)
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 17);

    private InterviewReminderService service;
    private NotificationCopyWriter copyWriter;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        copyWriter = new NotificationCopyWriter();
        service = new InterviewReminderService(
                upcomingNextStepsGateway,
                preferencesRepository,
                reminderSentRepository,
                notificationRepository,
                userEmailGateway,
                reminderMailer,
                copyWriter,
                26,
                fixedClock);
    }

    private UpcomingNextStep item(UUID userId, UUID applicationId, String label,
                                   LocalDate stepDate, String company) {
        return UpcomingNextStep.builder()
                .userId(userId)
                .applicationId(applicationId)
                .label(label)
                .stepDate(stepDate)
                .company(company)
                .status("interviewing")
                .build();
    }

    private NotificationPreferences prefs(UUID userId, boolean interviewReminders, boolean interviewReminderEmail) {
        return NotificationPreferences.builder()
                .userId(userId)
                .weeklyDigestEmail(true)
                .inAppNotificationsEnabled(true)
                .interviewReminders(interviewReminders)
                .interviewReminderEmail(interviewReminderEmail)
                .ghostedAlert(true)
                .build();
    }

    private NotificationPreferences prefs(UUID userId, boolean weeklyDigestEmail,
                                           boolean interviewReminders, boolean interviewReminderEmail) {
        return NotificationPreferences.builder()
                .userId(userId)
                .weeklyDigestEmail(weeklyDigestEmail)
                .inAppNotificationsEnabled(true)
                .interviewReminders(interviewReminders)
                .interviewReminderEmail(interviewReminderEmail)
                .ghostedAlert(true)
                .build();
    }

    // TC-101
    @Test
    @DisplayName("creates_in_app_notification_when_h24_fire_instant_reached")
    void createsInAppNotificationWhenH24FireInstantReached() {
        // TOMORROW -> H24 fires (start-of-tomorrow - 24h = 2026-06-17T00:00Z <= NOW).
        // H1 for TOMORROW fires at 2026-06-17T23:00Z which is > NOW, so H1 branch not reached.
        UpcomingNextStep step = item(U1, A1, "Interview with Product Manager", TOMORROW, "Acme Corp");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, false)));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(false);
        // H1 check not reached - do not stub it
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());

        service.run();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(U1);
        assertThat(saved.getType()).isEqualTo(NotificationType.INTERVIEW_REMINDER);

        ArgumentCaptor<InterviewReminderSent> sentCaptor = ArgumentCaptor.forClass(InterviewReminderSent.class);
        verify(reminderSentRepository, times(1)).save(sentCaptor.capture());
        assertThat(sentCaptor.getValue().getReminderOffset()).isEqualTo(ReminderOffset.H24);

        // H1 fire instant for TOMORROW (2026-06-17T23:00Z) is NOT yet reached at NOW
        verify(reminderSentRepository, never()).save(argThatOffset(ReminderOffset.H1));
    }

    // TC-102
    @Test
    @DisplayName("h24_fire_instant_computed_from_next_step_date_start_of_day_minus_24h")
    void h24FireInstantComputedCorrectly() {
        LocalDate d = LocalDate.of(2026, 6, 18);
        UpcomingNextStep step = item(U1, A1, "Interview", d, "Acme");

        // Sub-case 1: now = startOfDay(D) - 24h - 1min -> H24 NOT fired
        // At 2026-06-16T23:59:00Z, fire instant (2026-06-17T00:00:00Z) not yet reached.
        Instant beforeFire = Instant.parse("2026-06-16T23:59:00Z");
        Clock beforeClock = Clock.fixed(beforeFire, ZoneOffset.UTC);
        InterviewReminderService beforeService = new InterviewReminderService(
                upcomingNextStepsGateway, preferencesRepository, reminderSentRepository,
                notificationRepository, userEmailGateway, reminderMailer, copyWriter, 26, beforeClock);

        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        // preferencesRepository not called when H24 fire instant not reached
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());

        beforeService.run();

        verify(notificationRepository, never()).save(any());
        verify(reminderSentRepository, never()).save(any());

        // Sub-case 2: now = exactly the fire instant -> H24 fires
        // At 2026-06-17T00:00:00Z, the H24 fire instant is exactly reached.
        // H1 for 2026-06-18 fires at 2026-06-17T23:00:00Z, not yet reached.
        Instant atFire = Instant.parse("2026-06-17T00:00:00Z");
        Clock atClock = Clock.fixed(atFire, ZoneOffset.UTC);
        InterviewReminderService atService = new InterviewReminderService(
                upcomingNextStepsGateway, preferencesRepository, reminderSentRepository,
                notificationRepository, userEmailGateway, reminderMailer, copyWriter, 26, atClock);

        org.mockito.Mockito.clearInvocations(upcomingNextStepsGateway, preferencesRepository,
                reminderSentRepository, notificationRepository, userEmailGateway);
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, false)));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(false);
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());

        atService.run();

        verify(notificationRepository, times(1)).save(any());
        verify(reminderSentRepository, times(1)).save(any());
    }

    // TC-103
    @Test
    @DisplayName("h1_reminder_fires_independently_after_h24_already_sent")
    void h1ReminderFiresIndependentlyAfterH24AlreadySent() {
        // TODAY -> both H24 and H1 fire instants reached at NOW=2026-06-17T01:00Z
        UpcomingNextStep step = item(U1, A1, "Interview with Product Manager", TODAY, "Acme Corp");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, false)));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(true);
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H1)).thenReturn(false);
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());

        service.run();

        ArgumentCaptor<InterviewReminderSent> sentCaptor = ArgumentCaptor.forClass(InterviewReminderSent.class);
        verify(reminderSentRepository, times(1)).save(sentCaptor.capture());
        assertThat(sentCaptor.getValue().getReminderOffset()).isEqualTo(ReminderOffset.H1);

        // H24 must NOT be re-saved
        verify(reminderSentRepository, never()).save(argThatOffset(ReminderOffset.H24));
        verify(notificationRepository, times(1)).save(any());
    }

    // TC-104 / CR-153-U-010 regression: mailer invoked when both toggles are on
    @Test
    @DisplayName("sends_email_when_interviewReminderEmail_true")
    void sendsEmailWhenInterviewReminderEmailTrue() {
        // TOMORROW -> H24 fires only (H1 fire instant not yet reached at NOW)
        UpcomingNextStep step = item(U1, A1, "Interview with Product Manager", TOMORROW, "Acme Corp");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, true)));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(false);
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(anySet())).thenReturn(Map.of(U1, "u1@example.com"));

        service.run();

        verify(notificationRepository, times(1)).save(any());
        verify(reminderMailer, times(1)).send(eq("u1@example.com"), eq(step), eq(ReminderOffset.H24));

        ArgumentCaptor<InterviewReminderSent> sentCaptor = ArgumentCaptor.forClass(InterviewReminderSent.class);
        verify(reminderSentRepository, times(1)).save(sentCaptor.capture());
        assertThat(sentCaptor.getValue().getChannels()).contains("in_app").contains("email");
    }

    // TC-105
    @Test
    @DisplayName("does_not_resend_already_sent_h24_reminder")
    void doesNotResendAlreadySentH24Reminder() {
        // TOMORROW -> H24 fire instant reached but already sent; H1 not yet reached
        UpcomingNextStep step = item(U1, A1, "Interview", TOMORROW, "Acme");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, true)));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(true);
        // H1 fire instant not reached for TOMORROW -> exists(H1) not called
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of(U1, "u1@example.com"));

        service.run();

        verify(notificationRepository, never()).save(any());
        verify(reminderMailer, never()).send(any(), any(), any());
        verify(reminderSentRepository, never()).save(any());
    }

    // TC-106
    @Test
    @DisplayName("h24_skip_does_not_affect_independent_h1_check")
    void h24SkipDoesNotAffectIndependentH1Check() {
        // TODAY -> H24 already sent, H1 fire instant reached, H1 not sent
        UpcomingNextStep step = item(U1, A1, "Interview", TODAY, "Acme");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, true)));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(true);
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H1)).thenReturn(false);
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of(U1, "u1@example.com"));

        service.run();

        // H24 skipped
        verify(reminderSentRepository, never()).save(argThatOffset(ReminderOffset.H24));
        // H1 fires
        verify(notificationRepository, times(1)).save(any());
        verify(reminderMailer, times(1)).send(eq("u1@example.com"), eq(step), eq(ReminderOffset.H1));
        ArgumentCaptor<InterviewReminderSent> sentCaptor = ArgumentCaptor.forClass(InterviewReminderSent.class);
        verify(reminderSentRepository, times(1)).save(sentCaptor.capture());
        assertThat(sentCaptor.getValue().getReminderOffset()).isEqualTo(ReminderOffset.H1);
    }

    // TC-107
    @Test
    @DisplayName("master_switch_off_skips_user_entirely_no_row_written")
    void masterSwitchOffSkipsUserEntirelyNoRowWritten() {
        UpcomingNextStep step = item(U1, A1, "Interview", TOMORROW, "Acme");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, false, true)));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of(U1, "u1@example.com"));

        service.run();

        verify(notificationRepository, never()).save(any());
        verify(reminderMailer, never()).send(any(), any(), any());
        verify(reminderSentRepository, never()).save(any());
    }

    // TC-108
    @Test
    @DisplayName("user_with_no_preferences_row_treated_as_master_on_email_on")
    void userWithNoPreferencesRowTreatedAsMasterOnEmailOn() {
        // TOMORROW -> H24 fires only
        UpcomingNextStep step = item(U2, A2, "Interview", TOMORROW, "Acme");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U2)).thenReturn(Optional.empty());
        when(reminderSentRepository.exists(U2, A2, ReminderOffset.H24)).thenReturn(false);
        // H1 not reached -> exists(H1) not called
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of(U2, "u2@example.com"));

        service.run();

        verify(notificationRepository, times(1)).save(any());
        verify(reminderMailer, times(1)).send(eq("u2@example.com"), eq(step), eq(ReminderOffset.H24));
        ArgumentCaptor<InterviewReminderSent> sentCaptor = ArgumentCaptor.forClass(InterviewReminderSent.class);
        verify(reminderSentRepository, times(1)).save(sentCaptor.capture());
        assertThat(sentCaptor.getValue().getChannels()).contains("in_app").contains("email");
    }

    // TC-109 / CR-153-U-011 regression: mailer NOT invoked when email toggle is off
    @Test
    @DisplayName("in_app_only_when_interviewReminderEmail_false")
    void inAppOnlyWhenInterviewReminderEmailFalse() {
        // TOMORROW -> H24 fires only; email disabled -> in_app only
        UpcomingNextStep step = item(U1, A1, "Interview", TOMORROW, "Acme");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, false)));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(false);
        // H1 not reached for TOMORROW at NOW
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());

        service.run();

        verify(notificationRepository, times(1)).save(any());
        verify(reminderMailer, never()).send(any(), any(), any());
        ArgumentCaptor<InterviewReminderSent> sentCaptor = ArgumentCaptor.forClass(InterviewReminderSent.class);
        verify(reminderSentRepository, times(1)).save(sentCaptor.capture());
        assertThat(sentCaptor.getValue().getChannels()).isEqualTo("in_app");
        assertThat(sentCaptor.getValue().getChannels()).doesNotContain("email");
    }

    // TC-110
    @Test
    @DisplayName("reminder_content_includes_label_date_and_company")
    void reminderContentIncludesLabelDateAndCompany() {
        // Use TOMORROW (2026-06-18) -> H24 fires, H1 not reached
        LocalDate stepDate = TOMORROW;
        UpcomingNextStep step = item(U1, A1, "Interview with Product Manager", stepDate, "Acme Corp");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, true)));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(false);
        // H1 not reached -> not called
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of(U1, "u1@example.com"));

        service.run();

        ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(notifCaptor.capture());
        Notification saved = notifCaptor.getValue();
        assertThat(saved.getTitle()).isEqualTo("⏰ Countdown to showtime!");
        assertThat(saved.getMessage()).isEqualTo(copyWriter.interviewReminderMessage(
                "Interview with Product Manager", "Acme Corp", stepDate.toString(), ReminderOffset.H24));
        String content = saved.getTitle() + " " + saved.getMessage();
        assertThat(content).contains("Interview with Product Manager");
        assertThat(content).contains(stepDate.toString());
        assertThat(content).contains("Acme Corp");

        ArgumentCaptor<UpcomingNextStep> stepCaptor = ArgumentCaptor.forClass(UpcomingNextStep.class);
        verify(reminderMailer, times(1)).send(anyString(), stepCaptor.capture(), any());
        UpcomingNextStep sentStep = stepCaptor.getValue();
        assertThat(sentStep.getLabel()).contains("Interview with Product Manager");
        assertThat(sentStep.getStepDate()).isEqualTo(stepDate);
        assertThat(sentStep.getCompany()).isEqualTo("Acme Corp");
    }

    // TC-111
    @Test
    @DisplayName("no_upcoming_items_results_in_no_op")
    void noUpcomingItemsResultsInNoOp() {
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of());

        assertDoesNotThrow(() -> service.run());

        verify(preferencesRepository, never()).findByUserId(any());
        verify(reminderSentRepository, never()).exists(any(), any(), any());
        verify(notificationRepository, never()).save(any());
        verify(userEmailGateway, never()).fetchEmails(any());
        verify(reminderMailer, never()).send(any(), any(), any());
    }

    // TC-112
    @Test
    @DisplayName("application_outside_window_is_not_returned_by_gateway_so_no_reminder_this_tick")
    void applicationOutsideWindowNotReturnedByGatewaySoNoReminderThisTick() {
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of());

        assertDoesNotThrow(() -> service.run());

        verify(preferencesRepository, never()).findByUserId(any());
        verify(notificationRepository, never()).save(any());
        verify(reminderSentRepository, never()).save(any());
        verify(reminderMailer, never()).send(any(), any(), any());
    }

    // TC-113
    @Test
    @DisplayName("email_only_configuration_does_not_exist_in_app_always_created_when_master_on")
    void inAppAlwaysCreatedRegardlessOfEmailFlag() {
        // TOMORROW -> H24 fires, H1 not reached
        UpcomingNextStep step = item(U1, A1, "Interview", TOMORROW, "Acme");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // With emailEnabled=true
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(false);
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, true)));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of(U1, "u1@example.com"));
        service.run();
        verify(notificationRepository, times(1)).save(any());

        // Reset for emailEnabled=false
        org.mockito.Mockito.clearInvocations(notificationRepository, reminderSentRepository, reminderMailer,
                preferencesRepository, userEmailGateway, reminderSentRepository);
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(false);
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, false)));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());
        service.run();
        verify(notificationRepository, times(1)).save(any());
    }

    // TC-114
    @Test
    @DisplayName("missing_company_name_omits_company_without_placeholder")
    void missingCompanyNameOmitsCompanyWithoutPlaceholder() {
        // TOMORROW -> H24 fires, H1 not reached
        UpcomingNextStep step = item(U1, A1, "Interview with Product Manager", TOMORROW, null);
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, true)));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(false);
        // H1 not reached for TOMORROW at NOW
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of(U1, "u1@example.com"));

        service.run();

        ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(notifCaptor.capture());
        String content = notifCaptor.getValue().getTitle() + " " + notifCaptor.getValue().getMessage();
        assertThat(content.toLowerCase()).doesNotContain("null");
        assertThat(content.toLowerCase()).doesNotContain("undefined");
        assertThat(content).contains("Interview with Product Manager");

        verify(reminderSentRepository, times(1)).save(any());
    }

    // TC-115
    @Test
    @DisplayName("label_less_items_are_never_returned_by_gateway_so_never_reminded")
    void labelLessItemsAreNeverReturnedByGateway() {
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of());

        assertDoesNotThrow(() -> service.run());

        verify(preferencesRepository, never()).findByUserId(any());
        verify(notificationRepository, never()).save(any());
    }

    // TC-116
    @Test
    @DisplayName("terminal_status_items_are_never_returned_by_gateway_so_never_reminded")
    void terminalStatusItemsAreNeverReturnedByGateway() {
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of());

        assertDoesNotThrow(() -> service.run());

        verify(preferencesRepository, never()).findByUserId(any());
        verify(notificationRepository, never()).save(any());
    }

    // TC-117
    @Test
    @DisplayName("mid_flight_terminalisation_h1_not_created_when_item_disappears")
    void midFlightTerminalisationH1NotCreatedWhenItemDisappears() {
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of());

        service.run();

        verify(notificationRepository, never()).save(any());
        verify(reminderSentRepository, never()).save(argThatOffset(ReminderOffset.H1));
    }

    // TC-118
    @Test
    @DisplayName("reschedule_after_h24_sent_does_not_re_arm_h24_but_h1_fires_for_new_date")
    void rescheduleAfterH24SentDoesNotReArmH24ButH1FiresForNewDate() {
        // now=2026-06-17T01:00Z, new nextStepDate=2026-06-17 -> both H24 and H1 fire instants reached
        LocalDate rescheduledDate = TODAY;
        UpcomingNextStep step = item(U1, A1, "Interview", rescheduledDate, "Acme");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, true)));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(true); // already sent for old date
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H1)).thenReturn(false);
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of(U1, "u1@example.com"));

        service.run();

        // H24 not re-sent (idempotency key hit)
        verify(reminderSentRepository, never()).save(argThatOffset(ReminderOffset.H24));
        // H1 fires exactly once
        ArgumentCaptor<InterviewReminderSent> sentCaptor = ArgumentCaptor.forClass(InterviewReminderSent.class);
        verify(reminderSentRepository, times(1)).save(sentCaptor.capture());
        assertThat(sentCaptor.getValue().getReminderOffset()).isEqualTo(ReminderOffset.H1);
        assertThat(sentCaptor.getValue().getNextStepDate()).isEqualTo(rescheduledDate);
        verify(notificationRepository, times(1)).save(any());
    }

    private InterviewReminderSent argThatOffset(ReminderOffset offset) {
        return org.mockito.ArgumentMatchers.argThat(s -> s.getReminderOffset() == offset);
    }

    // TC-IR-U-30
    @Test
    @DisplayName("TC-IR-U-30: sendReminder sets applicationId on the saved Notification (H24 offset)")
    void sendReminderSetsApplicationIdOnSavedNotificationH24() {
        UpcomingNextStep step = item(U1, A1, "Interview with Product Manager", TOMORROW, "Acme Corp");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, false)));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(false);
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());

        service.run();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getApplicationId()).isEqualTo(A1);
    }

    // TC-IR-U-31
    @Test
    @DisplayName("TC-IR-U-31: sendReminder sets applicationId on the saved Notification (H1 offset)")
    void sendReminderSetsApplicationIdOnSavedNotificationH1() {
        UpcomingNextStep step = item(U1, A2, "Interview with Product Manager", TODAY, "Acme Corp");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, false)));
        when(reminderSentRepository.exists(U1, A2, ReminderOffset.H24)).thenReturn(true);
        when(reminderSentRepository.exists(U1, A2, ReminderOffset.H1)).thenReturn(false);
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());

        service.run();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getApplicationId()).isEqualTo(A2);
    }

    // TC-IR-U-32
    @Test
    @DisplayName("TC-IR-U-32: buildMessage names the company unconditionally when known (H24)")
    void buildMessageNamesCompanyUnconditionallyH24() {
        UpcomingNextStep step = item(U1, A1, "Interview", TOMORROW, "Acme Corp");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, false)));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(false);
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());

        service.run();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("Acme Corp");
    }

    // TC-IR-U-33
    @Test
    @DisplayName("TC-IR-U-33: buildMessage names the company unconditionally when known (H1)")
    void buildMessageNamesCompanyUnconditionallyH1() {
        UpcomingNextStep step = item(U1, A1, "Interview", TODAY, "Globex");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, false)));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(true);
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H1)).thenReturn(false);
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());

        service.run();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("Globex");
    }

    // TC-IR-U-34
    @Test
    @DisplayName("TC-IR-U-34: buildMessage falls back gracefully when company is null/unknown")
    void buildMessageFallsBackGracefullyWhenCompanyUnknown() {
        UpcomingNextStep step = item(U1, A1, "Interview with Product Manager", TOMORROW, null);
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, false)));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(false);
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());

        service.run();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getMessage().toLowerCase()).doesNotContain("null");
        assertThat(saved.getMessage()).doesNotContain("with  ").doesNotContain("with .").doesNotContain("  ");
        assertThat(saved.getMessage()).isEqualTo(copyWriter.interviewReminderMessage(
                "Interview with Product Manager", null, TOMORROW.toString(), ReminderOffset.H24));
        assertThat(saved.getApplicationId()).isEqualTo(A1);
    }

    // IR-U-35
    @Test
    @DisplayName("IR-U-35: H24 reminder persists the writer's exact title/message and keeps applicationId set")
    void h24ReminderPersistsExactCopyAndKeepsApplicationId() {
        UpcomingNextStep step = item(U1, A1, "Onsite interview", TOMORROW, "Acme Corp");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, false)));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(false);
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());

        service.run();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("⏰ Countdown to showtime!");
        assertThat(saved.getMessage()).isEqualTo(
                "Your Onsite interview with Acme Corp is coming up in about 24 hours (" + TOMORROW
                        + "). Take a breath, review your notes, and go show them what you've got.");
        assertThat(saved.getApplicationId()).isEqualTo(A1);
    }

    // IR-U-36
    @Test
    @DisplayName("IR-U-36: H1 reminder persists the writer's exact title/message, distinct from H24, applicationId set")
    void h1ReminderPersistsExactCopyDistinctFromH24() {
        UpcomingNextStep step = item(U1, A2, "Onsite interview", TODAY, "Acme Corp");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, false)));
        when(reminderSentRepository.exists(U1, A2, ReminderOffset.H24)).thenReturn(true);
        when(reminderSentRepository.exists(U1, A2, ReminderOffset.H1)).thenReturn(false);
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());

        service.run();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("🎤 You're up soon!");
        assertThat(saved.getMessage()).isEqualTo(
                "Your Onsite interview with Acme Corp kicks off in about 1 hour (" + TODAY
                        + "). Grab some water, take a breath, you've got this.");
        assertThat(saved.getApplicationId()).isEqualTo(A2);
        assertThat(saved.getTitle()).isNotEqualTo("⏰ Countdown to showtime!");
    }

    // IR-U-37
    @Test
    @DisplayName("IR-U-37: fallback when company absent preserves applicationId")
    void fallbackWhenCompanyAbsentPreservesApplicationId() {
        UpcomingNextStep step = item(U1, A1, "Interview with Product Manager", TOMORROW, null);
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, false)));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(false);
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());

        service.run();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getMessage()).isEqualTo(copyWriter.interviewReminderMessage(
                "Interview with Product Manager", null, TOMORROW.toString(), ReminderOffset.H24));
        assertThat(saved.getApplicationId()).isEqualTo(A1);
    }

    // IR-U-38: fallback when label absent is exercised at the NotificationCopyWriter level
    // (CW-U-10 / CW-U-14) since the real upstream gateway never returns a null label for an
    // upcoming next step; this service-level test documents that the service passes the label
    // through unchanged (no service-side defaulting) and that the writer's "interview" fallback
    // composes correctly end-to-end if a blank label were ever to reach it.
    @Test
    @DisplayName("IR-U-38: blank label still produces a graceful generic-label fallback end-to-end")
    void blankLabelStillProducesGracefulFallbackEndToEnd() {
        UpcomingNextStep step = item(U1, A1, "", TOMORROW, "Acme Corp");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, false)));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(false);
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(any())).thenReturn(Map.of());

        service.run();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getMessage()).contains("interview with Acme Corp");
        assertThat(saved.getMessage().toLowerCase()).doesNotContain("null");
        assertThat(saved.getApplicationId()).isEqualTo(A1);
    }

    // TC-209-U-03 (AC-3): weeklyDigestEmail OFF does not suppress the interview-reminder email;
    // weeklyDigestEmail ON produces byte-identical mailer/channels behaviour (toggle independence).
    @Test
    @DisplayName("TC-209-U-03 (AC-3): weeklyDigestEmail value has zero observable effect on interview-reminder email")
    void weeklyDigestEmailValueHasZeroObservableEffectOnInterviewReminderEmail() {
        UpcomingNextStep step = item(U1, A1, "Interview with Product Manager", TOMORROW, "Acme Corp");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(anySet())).thenReturn(Map.of(U1, "u1@example.com"));

        // Sub-case 1: weeklyDigestEmail = false, interviewReminders/interviewReminderEmail = true
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(false);
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, false, true, true)));

        service.run();

        verify(reminderMailer, times(1)).send(eq("u1@example.com"), eq(step), eq(ReminderOffset.H24));
        ArgumentCaptor<InterviewReminderSent> sentCaptorOff = ArgumentCaptor.forClass(InterviewReminderSent.class);
        verify(reminderSentRepository, times(1)).save(sentCaptorOff.capture());
        String channelsWithDigestOff = sentCaptorOff.getValue().getChannels();
        assertThat(channelsWithDigestOff).contains("in_app").contains("email");

        // Sub-case 2: identical prefs except weeklyDigestEmail = true -> same observable behaviour
        org.mockito.Mockito.clearInvocations(notificationRepository, reminderSentRepository, reminderMailer,
                preferencesRepository, userEmailGateway);
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(false);
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, true, true)));
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(anySet())).thenReturn(Map.of(U1, "u1@example.com"));

        service.run();

        verify(reminderMailer, times(1)).send(eq("u1@example.com"), eq(step), eq(ReminderOffset.H24));
        ArgumentCaptor<InterviewReminderSent> sentCaptorOn = ArgumentCaptor.forClass(InterviewReminderSent.class);
        verify(reminderSentRepository, times(1)).save(sentCaptorOn.capture());
        String channelsWithDigestOn = sentCaptorOn.getValue().getChannels();
        assertThat(channelsWithDigestOn).isEqualTo(channelsWithDigestOff);

        // Regression guard: weeklyDigestEmail is never read by NotificationPreferences via any
        // getter other than isWeeklyDigestEmail() itself, which this service never calls -
        // proven indirectly here by behavioural equivalence regardless of its value.
    }

    // TC-209-U-07-NEW (AC-7): mailer throws -> in-app notification already saved before the
    // throw, sent-row written with channels="in_app" only, exception swallowed, run continues.
    @Test
    @DisplayName("TC-209-U-07-NEW (AC-7): mailer throws leaves in-app notification standing and channels=in_app only")
    void mailerThrowsLeavesInAppNotificationStandingAndChannelsInAppOnly() {
        UpcomingNextStep step = item(U1, A1, "Interview with Product Manager", TOMORROW, "Acme Corp");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(step));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, true)));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(false);
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(anySet())).thenReturn(Map.of(U1, "u1@example.com"));
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP timeout"))
                .when(reminderMailer).send(eq("u1@example.com"), any(), any());

        assertDoesNotThrow(() -> service.run());

        verify(notificationRepository, times(1)).save(any());
        ArgumentCaptor<InterviewReminderSent> sentCaptor = ArgumentCaptor.forClass(InterviewReminderSent.class);
        verify(reminderSentRepository, times(1)).save(sentCaptor.capture());
        assertThat(sentCaptor.getValue().getChannels()).isEqualTo("in_app");
    }

    // TC-209-U-08 (AC-8): auth-service unreachable while resolving emails -> in-app fires for
    // everyone regardless of their individual interviewReminderEmail value, no email sent, no crash.
    @Test
    @DisplayName("TC-209-U-08 (AC-8): auth-service unreachable resolving emails degrades to in-app-only for everyone")
    void authServiceUnreachableResolvingEmailsDegradesToInAppOnlyForEveryone() {
        UpcomingNextStep stepEmailOn = item(U1, A1, "Interview with Product Manager", TOMORROW, "Acme Corp");
        UpcomingNextStep stepEmailOff = item(U2, A2, "Phone screen", TOMORROW, "Globex");
        when(upcomingNextStepsGateway.fetch(26)).thenReturn(List.of(stepEmailOn, stepEmailOff));
        when(preferencesRepository.findByUserId(U1)).thenReturn(Optional.of(prefs(U1, true, true)));
        when(preferencesRepository.findByUserId(U2)).thenReturn(Optional.of(prefs(U2, true, false)));
        when(reminderSentRepository.exists(U1, A1, ReminderOffset.H24)).thenReturn(false);
        when(reminderSentRepository.exists(U2, A2, ReminderOffset.H24)).thenReturn(false);
        when(reminderSentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userEmailGateway.fetchEmails(anySet()))
                .thenThrow(new RuntimeException("auth-service unreachable"));

        assertDoesNotThrow(() -> service.run());

        verify(notificationRepository, times(2)).save(any());
        verify(reminderMailer, never()).send(any(), any(), any());

        ArgumentCaptor<InterviewReminderSent> sentCaptor = ArgumentCaptor.forClass(InterviewReminderSent.class);
        verify(reminderSentRepository, times(2)).save(sentCaptor.capture());
        assertThat(sentCaptor.getAllValues())
                .allSatisfy(sent -> assertThat(sent.getChannels()).isEqualTo("in_app"));
    }
}
