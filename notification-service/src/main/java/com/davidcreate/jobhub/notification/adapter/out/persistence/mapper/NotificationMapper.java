package com.davidcreate.jobhub.notification.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.notification.adapter.out.persistence.entity.NotificationEntity;
import com.davidcreate.jobhub.notification.domain.model.Notification;
import com.davidcreate.jobhub.notification.domain.model.NotificationType;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.ZoneOffset;

@ApplicationScoped
public class NotificationMapper {

    public Notification toDomain(NotificationEntity entity) {
        return Notification.builder()
                .id(entity.id)
                .userId(entity.userId)
                .type(NotificationType.valueOf(entity.type))
                .title(entity.title)
                .message(entity.message)
                .read(entity.read)
                .createdAt(entity.createdAt.toLocalDateTime())
                .applicationId(entity.applicationId)
                .build();
    }

    public NotificationEntity toEntity(Notification domain) {
        NotificationEntity entity = new NotificationEntity();
        entity.id = domain.getId();
        entity.userId = domain.getUserId();
        entity.type = domain.getType().name();
        entity.title = domain.getTitle();
        entity.message = domain.getMessage();
        entity.read = domain.isRead();
        entity.applicationId = domain.getApplicationId();
        if (domain.getCreatedAt() != null) {
            entity.createdAt = domain.getCreatedAt().atOffset(ZoneOffset.UTC);
        }
        return entity;
    }
}
