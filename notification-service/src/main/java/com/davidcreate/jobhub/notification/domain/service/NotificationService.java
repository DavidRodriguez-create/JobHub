package com.davidcreate.jobhub.notification.domain.service;

import com.davidcreate.jobhub.notification.domain.exception.NotificationNotFoundException;
import com.davidcreate.jobhub.notification.domain.model.ApplicationSummary;
import com.davidcreate.jobhub.notification.domain.model.Notification;
import com.davidcreate.jobhub.notification.domain.model.NotificationPage;
import com.davidcreate.jobhub.notification.domain.model.ReadStatusFilter;
import com.davidcreate.jobhub.notification.domain.port.in.DeleteNotificationUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.GetUnreadCountUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.ListNotificationsUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.MarkAllNotificationsReadUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.MarkNotificationReadUseCase;
import com.davidcreate.jobhub.notification.domain.port.out.ApplicationSummaryGateway;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class NotificationService implements ListNotificationsUseCase, GetUnreadCountUseCase,
        MarkNotificationReadUseCase, MarkAllNotificationsReadUseCase, DeleteNotificationUseCase {

    private static final Logger LOG = Logger.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final ApplicationSummaryGateway summaryGateway;

    public NotificationService(NotificationRepository repository, ApplicationSummaryGateway summaryGateway) {
        this.repository = repository;
        this.summaryGateway = summaryGateway;
    }

    @Override
    @Transactional
    public NotificationPage listNotifications(UUID userId, int page, int size, ReadStatusFilter readStatus) {
        List<Notification> content = repository.findByUserId(userId, page, size, readStatus);
        long totalElements = repository.countByUserId(userId, readStatus);
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

        List<Notification> enriched = enrichWithApplicationSummaries(content);

        return NotificationPage.builder()
                .content(enriched)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }

    /**
     * Enrich-at-read (ADR 0014, story #207): resolves company/jobTitle for every
     * distinct, non-null applicationId on the page in a single batched gateway call.
     * Never fails the page: a gateway error degrades to an empty resolution map, and
     * any applicationId missing from the result simply leaves that notification's
     * company/jobTitle null.
     */
    private List<Notification> enrichWithApplicationSummaries(List<Notification> notifications) {
        Set<UUID> applicationIds = notifications.stream()
                .map(Notification::getApplicationId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        if (applicationIds.isEmpty()) {
            return notifications;
        }

        Map<UUID, ApplicationSummary> summaries;
        try {
            summaries = summaryGateway.resolve(applicationIds);
        } catch (RuntimeException e) {
            LOG.warnf(e, "Failed to resolve application summaries for %d application id(s); " +
                    "degrading to unresolved company/jobTitle for this page", applicationIds.size());
            summaries = Collections.emptyMap();
        }

        Map<UUID, ApplicationSummary> resolved = summaries;
        return notifications.stream()
                .map(notification -> {
                    if (notification.getApplicationId() == null) {
                        return notification;
                    }
                    ApplicationSummary summary = resolved.get(notification.getApplicationId());
                    if (summary == null) {
                        return notification;
                    }
                    return notification.toBuilder()
                            .company(summary.getCompany())
                            .jobTitle(summary.getJobTitle())
                            .companyLogoUrl(summary.getCompanyLogoUrl())
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional
    public long getUnreadCount(UUID userId) {
        return repository.countByUserId(userId, ReadStatusFilter.UNREAD);
    }

    @Override
    @Transactional
    public void markNotificationRead(UUID userId, UUID notificationId) {
        Notification notification = repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        if (!notification.isRead()) {
            repository.markRead(notification.getId());
        }
    }

    @Override
    @Transactional
    public void markAllNotificationsRead(UUID userId) {
        repository.markAllRead(userId);
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID userId) {
        boolean deleted = repository.deleteByIdAndUser(id, userId);
        if (!deleted) {
            throw new NotificationNotFoundException(id);
        }
    }
}
