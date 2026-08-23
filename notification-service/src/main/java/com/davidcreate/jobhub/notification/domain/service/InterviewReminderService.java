package com.davidcreate.jobhub.notification.domain.service;

import com.davidcreate.jobhub.notification.domain.model.InterviewReminderSent;
import com.davidcreate.jobhub.notification.domain.model.Notification;
import com.davidcreate.jobhub.notification.domain.model.NotificationPreferences;
import com.davidcreate.jobhub.notification.domain.model.NotificationType;
import com.davidcreate.jobhub.notification.domain.model.ReminderOffset;
import com.davidcreate.jobhub.notification.domain.model.UpcomingNextStep;
import com.davidcreate.jobhub.notification.domain.port.in.SendInterviewRemindersUseCase;
import com.davidcreate.jobhub.notification.domain.port.out.InterviewReminderMailer;
import com.davidcreate.jobhub.notification.domain.port.out.InterviewReminderSentRepository;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationPreferencesRepository;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationRepository;
import com.davidcreate.jobhub.notification.domain.port.out.UpcomingNextStepsGateway;
import com.davidcreate.jobhub.notification.domain.port.out.UserEmailGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class InterviewReminderService implements SendInterviewRemindersUseCase {

    private static final Logger LOG = Logger.getLogger(InterviewReminderService.class);

    private final UpcomingNextStepsGateway upcomingNextStepsGateway;
    private final NotificationPreferencesRepository preferencesRepository;
    private final InterviewReminderSentRepository reminderSentRepository;
    private final NotificationRepository notificationRepository;
    private final UserEmailGateway userEmailGateway;
    private final InterviewReminderMailer reminderMailer;
    private final NotificationCopyWriter copyWriter;
    private final int withinHours;
    private final Clock clock;

    public InterviewReminderService(UpcomingNextStepsGateway upcomingNextStepsGateway,
                                     NotificationPreferencesRepository preferencesRepository,
                                     InterviewReminderSentRepository reminderSentRepository,
                                     NotificationRepository notificationRepository,
                                     UserEmailGateway userEmailGateway,
                                     InterviewReminderMailer reminderMailer,
                                     NotificationCopyWriter copyWriter) {
        this(upcomingNextStepsGateway, preferencesRepository, reminderSentRepository,
                notificationRepository, userEmailGateway, reminderMailer, copyWriter, 26, Clock.systemUTC());
    }

    @Inject
    public InterviewReminderService(UpcomingNextStepsGateway upcomingNextStepsGateway,
                                     NotificationPreferencesRepository preferencesRepository,
                                     InterviewReminderSentRepository reminderSentRepository,
                                     NotificationRepository notificationRepository,
                                     UserEmailGateway userEmailGateway,
                                     InterviewReminderMailer reminderMailer,
                                     NotificationCopyWriter copyWriter,
                                     @ConfigProperty(name = "notification.interview-reminder.within-hours", defaultValue = "26") int withinHours,
                                     Clock clock) {
        this.upcomingNextStepsGateway = upcomingNextStepsGateway;
        this.preferencesRepository = preferencesRepository;
        this.reminderSentRepository = reminderSentRepository;
        this.notificationRepository = notificationRepository;
        this.userEmailGateway = userEmailGateway;
        this.reminderMailer = reminderMailer;
        this.copyWriter = copyWriter;
        this.withinHours = withinHours;
        this.clock = clock;
    }

    @Override
    public void run() {
        List<UpcomingNextStep> items = upcomingNextStepsGateway.fetch(withinHours);
        if (items.isEmpty()) {
            LOG.info("Interview reminder run: no upcoming next steps, nothing to do");
            return;
        }

        // Resolve emails for all users who might receive email reminders.
        Set<UUID> userIds = items.stream().map(UpcomingNextStep::getUserId).collect(Collectors.toSet());
        Map<UUID, String> emails;
        try {
            emails = userEmailGateway.fetchEmails(userIds);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Interview reminder run: auth-service unreachable while resolving emails for %d users, processing in-app only", userIds.size());
            emails = Map.of();
        }

        Instant now = Instant.now(clock);

        for (UpcomingNextStep item : items) {
            try {
                processItem(item, now, emails);
            } catch (RuntimeException e) {
                LOG.errorf(e, "Interview reminder: failed processing userId=%s applicationId=%s, skipping",
                        item.getUserId(), item.getApplicationId());
            }
        }
    }

    private void processItem(UpcomingNextStep item, Instant now, Map<UUID, String> emails) {
        UUID userId = item.getUserId();
        UUID applicationId = item.getApplicationId();
        LocalDate stepDate = item.getStepDate();

        NotificationPreferences prefs = preferencesRepository.findByUserId(userId)
                .orElse(defaultPreferences(userId));

        if (!prefs.isInterviewReminders()) {
            LOG.debugf("Interview reminder: userId=%s has interview reminders disabled, skipping", userId);
            return;
        }

        boolean emailEnabled = prefs.isInterviewReminderEmail();
        String email = emails.get(userId);

        if (isH24FireInstantReached(stepDate, now) && !reminderSentRepository.exists(userId, applicationId, ReminderOffset.H24)) {
            sendReminder(item, ReminderOffset.H24, emailEnabled, email);
        }

        if (isH1FireInstantReached(stepDate, now) && !reminderSentRepository.exists(userId, applicationId, ReminderOffset.H1)) {
            sendReminder(item, ReminderOffset.H1, emailEnabled, email);
        }
    }

    private void sendReminder(UpcomingNextStep item, ReminderOffset offset, boolean emailEnabled, String email) {
        UUID userId = item.getUserId();
        UUID applicationId = item.getApplicationId();

        String title = copyWriter.interviewReminderTitle(offset);
        String message = copyWriter.interviewReminderMessage(
                item.getLabel(), item.getCompany(), item.getStepDate().toString(), offset);

        notificationRepository.save(Notification.builder()
                .userId(userId)
                .applicationId(applicationId)
                .type(NotificationType.INTERVIEW_REMINDER)
                .title(title)
                .message(message)
                .read(false)
                .build());

        String channels = "in_app";

        if (emailEnabled && email != null) {
            try {
                reminderMailer.send(email, item, offset);
                channels = "in_app,email";
            } catch (RuntimeException e) {
                LOG.errorf(e, "Interview reminder: failed to send email to userId=%s applicationId=%s offset=%s",
                        userId, applicationId, offset);
            }
        }

        reminderSentRepository.save(InterviewReminderSent.builder()
                .userId(userId)
                .applicationId(applicationId)
                .reminderOffset(offset)
                .nextStepDate(item.getStepDate())
                .channels(channels)
                .sentAt(Instant.now(clock))
                .build());

        LOG.infof("Interview reminder sent: userId=%s applicationId=%s offset=%s channels=%s",
                userId, applicationId, offset, channels);
    }

    /**
     * H24 fires when nextStepDate is tomorrow relative to "now": i.e., the start of
     * nextStepDate (midnight UTC) minus 24h is at or before now.
     * Since nextStepDate is day-granular, "tomorrow" means stepDate == today + 1.
     */
    private boolean isH24FireInstantReached(LocalDate stepDate, Instant now) {
        Instant fireInstant = stepDate.atStartOfDay(ZoneOffset.UTC).toInstant().minusSeconds(24 * 3600L);
        return !now.isBefore(fireInstant);
    }

    /**
     * H1 fires when nextStepDate is today: i.e., the start of nextStepDate (midnight UTC)
     * minus 1h is at or before now.
     */
    private boolean isH1FireInstantReached(LocalDate stepDate, Instant now) {
        Instant fireInstant = stepDate.atStartOfDay(ZoneOffset.UTC).toInstant().minusSeconds(3600L);
        return !now.isBefore(fireInstant);
    }

    /**
     * Default preferences when no row exists: both channels enabled (AC-5 / BR-2).
     */
    private NotificationPreferences defaultPreferences(UUID userId) {
        return NotificationPreferences.builder()
                .userId(userId)
                .weeklyDigestEmail(true)
                .inAppNotificationsEnabled(true)
                .interviewReminders(true)
                .interviewReminderEmail(true)
                .ghostedAlert(true)
                .build();
    }

    /**
     * Package-private for testing: allows injecting a controlled Clock.
     */
    static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
