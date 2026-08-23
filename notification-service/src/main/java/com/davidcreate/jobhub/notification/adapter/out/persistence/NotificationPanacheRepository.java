package com.davidcreate.jobhub.notification.adapter.out.persistence;

import com.davidcreate.jobhub.notification.adapter.out.persistence.entity.NotificationEntity;
import com.davidcreate.jobhub.notification.adapter.out.persistence.mapper.NotificationMapper;
import com.davidcreate.jobhub.notification.domain.model.Notification;
import com.davidcreate.jobhub.notification.domain.model.NotificationType;
import com.davidcreate.jobhub.notification.domain.model.ReadStatusFilter;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class NotificationPanacheRepository
        implements NotificationRepository, PanacheRepositoryBase<NotificationEntity, UUID> {

    private final NotificationMapper mapper;

    public NotificationPanacheRepository(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Notification save(Notification notification) {
        NotificationEntity entity = mapper.toEntity(notification);
        if (entity.id == null) {
            entity.id = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (entity.createdAt == null) {
            entity.createdAt = now;
        }
        entity.updatedAt = now;
        persist(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public List<Notification> findByUserId(UUID userId, int page, int size, ReadStatusFilter readStatus) {
        StringBuilder query = new StringBuilder("userId = :userId");
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        appendReadStatusFilter(query, params, readStatus);
        query.append(" ORDER BY createdAt DESC");

        PanacheQuery<NotificationEntity> panacheQuery = find(query.toString(), params);
        return panacheQuery.page(page, size).list().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long countByUserId(UUID userId, ReadStatusFilter readStatus) {
        StringBuilder query = new StringBuilder("userId = :userId");
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        appendReadStatusFilter(query, params, readStatus);

        return count(query.toString(), params);
    }

    @Override
    public Optional<Notification> findByIdAndUserId(UUID id, UUID userId) {
        return find("id = :id and userId = :userId", Map.of("id", id, "userId", userId))
                .firstResultOptional()
                .map(mapper::toDomain);
    }

    @Override
    public void markRead(UUID id) {
        update("read = true, updatedAt = :now where id = :id",
                Map.of("now", OffsetDateTime.now(), "id", id));
    }

    @Override
    public void markAllRead(UUID userId) {
        update("read = true, updatedAt = :now where userId = :userId and read = false",
                Map.of("now", OffsetDateTime.now(), "userId", userId));
    }

    @Override
    public boolean existsByUserIdAndType(UUID userId, NotificationType type) {
        return count("userId = :userId and type = :type",
                Map.of("userId", userId, "type", type.name())) > 0;
    }

    @Override
    @Transactional
    public boolean deleteByIdAndUser(UUID id, UUID userId) {
        return delete("id = ?1 and userId = ?2", id, userId) > 0;
    }

    private void appendReadStatusFilter(StringBuilder query, Map<String, Object> params, ReadStatusFilter readStatus) {
        switch (readStatus) {
            case READ -> query.append(" and read = true");
            case UNREAD -> query.append(" and read = false");
            case ALL -> { /* no additional filter */ }
        }
    }
}
