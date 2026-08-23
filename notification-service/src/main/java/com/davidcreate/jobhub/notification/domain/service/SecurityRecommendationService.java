package com.davidcreate.jobhub.notification.domain.service;

import com.davidcreate.jobhub.notification.domain.model.Notification;
import com.davidcreate.jobhub.notification.domain.model.NotificationType;
import com.davidcreate.jobhub.notification.domain.port.in.ProcessSecurityRecommendationsUseCase;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationRepository;
import com.davidcreate.jobhub.notification.domain.port.out.TwoFactorStatusGateway;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SecurityRecommendationService implements ProcessSecurityRecommendationsUseCase {

    private static final Logger LOG = Logger.getLogger(SecurityRecommendationService.class);

    private final TwoFactorStatusGateway twoFactorStatusGateway;
    private final NotificationRepository notificationRepository;
    private final NotificationCopyWriter copyWriter;

    public SecurityRecommendationService(TwoFactorStatusGateway twoFactorStatusGateway,
                                          NotificationRepository notificationRepository,
                                          NotificationCopyWriter copyWriter) {
        this.twoFactorStatusGateway = twoFactorStatusGateway;
        this.notificationRepository = notificationRepository;
        this.copyWriter = copyWriter;
    }

    @Override
    public void run() {
        List<UUID> userIds;
        try {
            userIds = twoFactorStatusGateway.fetchUsersWithoutTwoFactor();
        } catch (RuntimeException e) {
            LOG.errorf(e, "Security-recommendation run failed: could not retrieve users without 2FA");
            return;
        }

        if (userIds.isEmpty()) {
            LOG.info("Security-recommendation run: no users without 2FA found, nothing to do");
            return;
        }

        for (UUID userId : userIds) {
            processUser(userId);
        }
    }

    private void processUser(UUID userId) {
        boolean alreadyNotified;
        try {
            alreadyNotified = notificationRepository.existsByUserIdAndType(userId, NotificationType.SECURITY_RECOMMENDATION);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Security-recommendation: failed to check existing notification for user %s, skipping", userId);
            return;
        }

        if (alreadyNotified) {
            LOG.debugf("Security-recommendation: user %s already has a SECURITY_RECOMMENDATION notification, skipping", userId);
            return;
        }

        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .type(NotificationType.SECURITY_RECOMMENDATION)
                .title(copyWriter.securityRecommendationTitle())
                .message(copyWriter.securityRecommendationMessage())
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();

        try {
            notificationRepository.save(notification);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Security-recommendation: failed to write notification for user %s", userId);
        }
    }
}
