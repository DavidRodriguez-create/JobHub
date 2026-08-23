package com.davidcreate.jobhub.notification.domain.port.out;

import com.davidcreate.jobhub.notification.domain.model.Notification;
import com.davidcreate.jobhub.notification.domain.model.NotificationType;
import com.davidcreate.jobhub.notification.domain.model.ReadStatusFilter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    Notification save(Notification notification);

    List<Notification> findByUserId(UUID userId, int page, int size, ReadStatusFilter readStatus);

    long countByUserId(UUID userId, ReadStatusFilter readStatus);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    void markRead(UUID id);

    void markAllRead(UUID userId);

    boolean existsByUserIdAndType(UUID userId, NotificationType type);

    boolean deleteByIdAndUser(UUID id, UUID userId);
}
