package com.davidcreate.jobhub.notification.adapter.out.persistence;

import com.davidcreate.jobhub.notification.adapter.out.persistence.entity.NotificationPreferencesEntity;
import com.davidcreate.jobhub.notification.adapter.out.persistence.mapper.NotificationPreferencesMapper;
import com.davidcreate.jobhub.notification.domain.model.NotificationPreferences;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationPreferencesRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class NotificationPreferencesPanacheRepository
        implements NotificationPreferencesRepository, PanacheRepositoryBase<NotificationPreferencesEntity, UUID> {

    private final NotificationPreferencesMapper mapper;

    public NotificationPreferencesPanacheRepository(NotificationPreferencesMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<NotificationPreferences> findByUserId(UUID userId) {
        return find("userId", userId).firstResultOptional().map(mapper::toDomain);
    }

    @Override
    public NotificationPreferences upsert(NotificationPreferences preferences) {
        NotificationPreferencesEntity entity = find("userId", preferences.getUserId()).firstResult();
        OffsetDateTime now = OffsetDateTime.now();
        if (entity == null) {
            entity = mapper.toEntity(preferences);
            if (entity.id == null) {
                entity.id = UUID.randomUUID();
            }
            entity.createdAt = now;
            entity.updatedAt = now;
            persist(entity);
        } else {
            entity.weeklyDigestEmail = preferences.isWeeklyDigestEmail();
            entity.inAppNotificationsEnabled = preferences.isInAppNotificationsEnabled();
            entity.interviewReminders = preferences.isInterviewReminders();
            entity.interviewReminderEmail = preferences.isInterviewReminderEmail();
            entity.ghostedAlert = preferences.isGhostedAlert();
            entity.updatedAt = now;
        }
        return mapper.toDomain(entity);
    }

    @Override
    public List<UUID> findWeeklyDigestCandidateUserIds() {
        return getEntityManager()
                .createQuery("select e.userId from NotificationPreferencesEntity e where e.weeklyDigestEmail = true", UUID.class)
                .getResultList();
    }
}
