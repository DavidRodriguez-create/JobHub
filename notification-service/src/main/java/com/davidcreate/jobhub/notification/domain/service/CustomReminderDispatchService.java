package com.davidcreate.jobhub.notification.domain.service;

import com.davidcreate.jobhub.notification.domain.model.CustomReminder;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderChannel;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderStage;
import com.davidcreate.jobhub.notification.domain.model.Notification;
import com.davidcreate.jobhub.notification.domain.model.NotificationPreferences;
import com.davidcreate.jobhub.notification.domain.model.NotificationType;
import com.davidcreate.jobhub.notification.domain.port.in.DispatchDueCustomRemindersUseCase;
import com.davidcreate.jobhub.notification.domain.port.out.CustomReminderMailer;
import com.davidcreate.jobhub.notification.domain.port.out.CustomReminderRepository;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationPreferencesRepository;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationRepository;
import com.davidcreate.jobhub.notification.domain.port.out.UserEmailGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class CustomReminderDispatchService implements DispatchDueCustomRemindersUseCase {

    private static final Logger LOG = Logger.getLogger(CustomReminderDispatchService.class);

    private final CustomReminderRepository repository;
    private final NotificationPreferencesRepository preferencesRepository;
    private final NotificationRepository notificationRepository;
    private final UserEmailGateway userEmailGateway;
    private final CustomReminderMailer mailer;
    private final int batchSize;
    private final Clock clock;

    public CustomReminderDispatchService(CustomReminderRepository repository,
                                          NotificationPreferencesRepository preferencesRepository,
                                          NotificationRepository notificationRepository,
                                          UserEmailGateway userEmailGateway,
                                          CustomReminderMailer mailer) {
        this(repository, preferencesRepository, notificationRepository, userEmailGateway, mailer, 200, Clock.systemUTC());
    }

    @Inject
    public CustomReminderDispatchService(CustomReminderRepository repository,
                                          NotificationPreferencesRepository preferencesRepository,
                                          NotificationRepository notificationRepository,
                                          UserEmailGateway userEmailGateway,
                                          CustomReminderMailer mailer,
                                          @ConfigProperty(name = "notification.custom-reminder.batch-size", defaultValue = "200") int batchSize,
                                          Clock clock) {
        this.repository = repository;
        this.preferencesRepository = preferencesRepository;
        this.notificationRepository = notificationRepository;
        this.userEmailGateway = userEmailGateway;
        this.mailer = mailer;
        this.batchSize = batchSize;
        this.clock = clock;
    }

    @Override
    public void run() {
        Instant now = Instant.now(clock);
        List<CustomReminder> due = repository.findDue(now, batchSize);
        if (due.isEmpty()) {
            return;
        }

        Set<UUID> userIds = due.stream().map(CustomReminder::getUserId).collect(Collectors.toSet());
        Map<UUID, String> emails;
        try {
            emails = userEmailGateway.fetchEmails(userIds);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Custom reminder dispatch: auth-service unreachable while resolving emails for %d users, processing in-app only", userIds.size());
            emails = Map.of();
        }

        for (CustomReminder reminder : due) {
            try {
                dispatch(reminder, now, emails);
            } catch (RuntimeException e) {
                LOG.errorf(e, "Custom reminder dispatch: failed processing reminderId=%s userId=%s, skipping",
                        reminder.getId(), reminder.getUserId());
            }
        }
    }

    private void dispatch(CustomReminder reminder, Instant now, Map<UUID, String> emails) {
        if (reminder.getStatus() != com.davidcreate.jobhub.notification.domain.model.CustomReminderStatus.SCHEDULED) {
            LOG.debugf("Custom reminder dispatch: reminderId=%s is not SCHEDULED (status=%s), skipping",
                    reminder.getId(), reminder.getStatus());
            return;
        }

        UUID userId = reminder.getUserId();
        Set<CustomReminderChannel> requested = reminder.getChannels();
        Set<CustomReminderChannel> fired = EnumSet.noneOf(CustomReminderChannel.class);

        if (requested.contains(CustomReminderChannel.IN_APP)) {
            notificationRepository.save(Notification.builder()
                    .userId(userId)
                    .type(NotificationType.CUSTOM_REMINDER)
                    .title(reminder.getTitle())
                    .message(buildMessage(reminder))
                    .read(false)
                    .applicationId(reminder.getApplicationId())
                    .build());
            fired.add(CustomReminderChannel.IN_APP);
        }

        if (requested.contains(CustomReminderChannel.EMAIL)) {
            NotificationPreferences prefs = preferencesRepository.findByUserId(userId)
                    .orElse(defaultPreferences(userId));
            String email = emails.get(userId);

            if (prefs.isInterviewReminderEmail() && email != null) {
                try {
                    mailer.send(email, reminder);
                    fired.add(CustomReminderChannel.EMAIL);
                } catch (RuntimeException e) {
                    LOG.errorf(e, "Custom reminder dispatch: failed to send email for reminderId=%s userId=%s",
                            reminder.getId(), userId);
                }
            } else if (!prefs.isInterviewReminderEmail()) {
                LOG.warnf("Custom reminder dispatch: EMAIL channel gated off by master preference for userId=%s reminderId=%s",
                        userId, reminder.getId());
            }
        }

        boolean updated = repository.markFired(reminder.getId(), fired, now);
        if (!updated) {
            LOG.debugf("Custom reminder dispatch: reminderId=%s already fired by another tick, skipping mark", reminder.getId());
            return;
        }

        LOG.infof("Custom reminder fired: reminderId=%s userId=%s channelsFired=%s",
                reminder.getId(), userId, fired);
    }

    private String buildMessage(CustomReminder reminder) {
        StringBuilder sb = new StringBuilder();
        if (reminder.getNote() != null && !reminder.getNote().isBlank()) {
            sb.append(reminder.getNote());
        } else {
            sb.append(reminder.getTitle());
        }
        CustomReminderStage stage = reminder.getStage();
        if (stage != null) {
            sb.append(" (").append(stage).append(")");
        }
        return sb.toString();
    }

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
}
