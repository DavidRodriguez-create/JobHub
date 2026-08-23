package com.davidcreate.jobhub.notification.domain.service;

import com.davidcreate.jobhub.notification.domain.exception.ApplicationAlreadyGhostedException;
import com.davidcreate.jobhub.notification.domain.model.Notification;
import com.davidcreate.jobhub.notification.domain.model.NotificationPreferences;
import com.davidcreate.jobhub.notification.domain.model.NotificationType;
import com.davidcreate.jobhub.notification.domain.model.StaleApplication;
import com.davidcreate.jobhub.notification.domain.port.in.ProcessGhostedAlertsUseCase;
import com.davidcreate.jobhub.notification.domain.port.out.AlertMailer;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationPreferencesRepository;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationRepository;
import com.davidcreate.jobhub.notification.domain.port.out.StaleApplicationGateway;
import com.davidcreate.jobhub.notification.domain.port.out.UserEmailGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class GhostedAlertService implements ProcessGhostedAlertsUseCase {

    private static final Logger LOG = Logger.getLogger(GhostedAlertService.class);

    private final StaleApplicationGateway staleApplicationGateway;
    private final NotificationPreferencesRepository preferencesRepository;
    private final NotificationRepository notificationRepository;
    private final UserEmailGateway userEmailGateway;
    private final AlertMailer alertMailer;
    private final NotificationCopyWriter copyWriter;
    private final int staleDays;

    public GhostedAlertService(StaleApplicationGateway staleApplicationGateway,
                                NotificationPreferencesRepository preferencesRepository,
                                NotificationRepository notificationRepository,
                                UserEmailGateway userEmailGateway,
                                AlertMailer alertMailer,
                                NotificationCopyWriter copyWriter) {
        this(staleApplicationGateway, preferencesRepository, notificationRepository,
                userEmailGateway, alertMailer, copyWriter, 14);
    }

    @Inject
    public GhostedAlertService(StaleApplicationGateway staleApplicationGateway,
                                NotificationPreferencesRepository preferencesRepository,
                                NotificationRepository notificationRepository,
                                UserEmailGateway userEmailGateway,
                                AlertMailer alertMailer,
                                NotificationCopyWriter copyWriter,
                                @ConfigProperty(name = "notification.ghosted.stale-days", defaultValue = "14") int staleDays) {
        this.staleApplicationGateway = staleApplicationGateway;
        this.preferencesRepository = preferencesRepository;
        this.notificationRepository = notificationRepository;
        this.userEmailGateway = userEmailGateway;
        this.alertMailer = alertMailer;
        this.copyWriter = copyWriter;
        this.staleDays = staleDays;
    }

    @Override
    public void run() {
        List<StaleApplication> staleApps;
        try {
            staleApps = staleApplicationGateway.listStaleApplications(staleDays);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Ghosted-alert run failed: could not retrieve stale applications");
            return;
        }

        if (staleApps.isEmpty()) {
            LOG.info("Ghosted-alert run: no stale applications found, nothing to do");
            return;
        }

        // Batch-fetch emails for all opted-in users at once to minimise outbound calls.
        Set<UUID> optedInUserIds = collectOptedInUserIds(staleApps);
        Map<UUID, String> emails = fetchEmailsSilently(optedInUserIds);

        for (StaleApplication app : staleApps) {
            if (!optedInUserIds.contains(app.getUserId())) {
                // User preference was ghostedAlert=false, skip entirely
                continue;
            }
            processApp(app, emails.get(app.getUserId()));
        }
    }

    private Set<UUID> collectOptedInUserIds(List<StaleApplication> staleApps) {
        Set<UUID> optedIn = new HashSet<>();
        for (StaleApplication app : staleApps) {
            boolean ghostedAlertEnabled = preferencesRepository.findByUserId(app.getUserId())
                    .map(NotificationPreferences::isGhostedAlert)
                    .orElse(true); // Default: opted in
            if (ghostedAlertEnabled) {
                optedIn.add(app.getUserId());
            }
        }
        return optedIn;
    }

    private Map<UUID, String> fetchEmailsSilently(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            return userEmailGateway.fetchEmails(userIds);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Ghosted-alert run: failed to fetch emails for %d users, email step will be skipped",
                    userIds.size());
            return Map.of();
        }
    }

    private void processApp(StaleApplication app, String email) {
        // Step 1: Update status to ghosted
        try {
            staleApplicationGateway.updateApplicationStatusToGhosted(app.getId());
        } catch (ApplicationAlreadyGhostedException e) {
            LOG.debugf("Ghosted-alert: application %s is already in a terminal state (409), skipping", app.getId());
            return;
        } catch (RuntimeException e) {
            LOG.errorf(e, "Ghosted-alert: failed to update status for application %s, skipping notification",
                    app.getId());
            return;
        }

        // Step 2: Write in-app notification
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(app.getUserId())
                .applicationId(app.getId())
                .type(NotificationType.GHOSTED_ALERT)
                .title(copyWriter.ghostedAlertTitle())
                .message(copyWriter.ghostedAlertMessage(app.getJobTitle()))
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();

        try {
            notificationRepository.save(notification);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Ghosted-alert: failed to write notification for application %s, skipping email",
                    app.getId());
            return;
        }

        // Step 3: Send email if user is verified
        if (email != null) {
            try {
                alertMailer.sendGhostedAlert(email, app);
            } catch (RuntimeException e) {
                LOG.errorf(e, "Ghosted-alert: failed to send email to user %s for application %s",
                        app.getUserId(), app.getId());
            }
        }
    }

}
