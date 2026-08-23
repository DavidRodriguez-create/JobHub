package com.davidcreate.jobhub.notification.domain.service;

import com.davidcreate.jobhub.notification.domain.model.NotificationPreferences;
import com.davidcreate.jobhub.notification.domain.port.in.GetPreferencesUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.UpdatePreferencesUseCase;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationPreferencesRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class NotificationPreferencesService implements GetPreferencesUseCase, UpdatePreferencesUseCase {

    private final NotificationPreferencesRepository repository;

    public NotificationPreferencesService(NotificationPreferencesRepository repository) {
        this.repository = repository;
    }

    @Override
    public NotificationPreferences getPreferences(UUID userId) {
        return repository.findByUserId(userId)
                .orElseGet(() -> defaults(userId));
    }

    @Override
    @Transactional
    public NotificationPreferences updatePreferences(UUID userId,
                                                     Boolean weeklyDigestEmail,
                                                     Boolean inAppNotificationsEnabled,
                                                     Boolean interviewReminders,
                                                     Boolean interviewReminderEmail,
                                                     Boolean ghostedAlert) {
        NotificationPreferences current = repository.findByUserId(userId)
                .orElseGet(() -> defaults(userId));

        NotificationPreferences merged = NotificationPreferences.builder()
                .id(current.getId())
                .userId(userId)
                .weeklyDigestEmail(weeklyDigestEmail != null ? weeklyDigestEmail : current.isWeeklyDigestEmail())
                .inAppNotificationsEnabled(inAppNotificationsEnabled != null
                        ? inAppNotificationsEnabled : current.isInAppNotificationsEnabled())
                .interviewReminders(interviewReminders != null ? interviewReminders : current.isInterviewReminders())
                .interviewReminderEmail(interviewReminderEmail != null ? interviewReminderEmail : current.isInterviewReminderEmail())
                .ghostedAlert(ghostedAlert != null ? ghostedAlert : current.isGhostedAlert())
                .build();

        return repository.upsert(merged);
    }

    private NotificationPreferences defaults(UUID userId) {
        return NotificationPreferences.builder()
                .userId(userId)
                .weeklyDigestEmail(true)
                .inAppNotificationsEnabled(false)
                .interviewReminders(true)
                .interviewReminderEmail(true)
                .ghostedAlert(true)
                .build();
    }
}
