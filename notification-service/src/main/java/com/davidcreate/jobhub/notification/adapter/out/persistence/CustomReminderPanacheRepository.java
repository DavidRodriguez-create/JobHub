package com.davidcreate.jobhub.notification.adapter.out.persistence;

import com.davidcreate.jobhub.notification.adapter.out.persistence.entity.CustomReminderEntity;
import com.davidcreate.jobhub.notification.adapter.out.persistence.mapper.CustomReminderEntityMapper;
import com.davidcreate.jobhub.notification.domain.model.CustomReminder;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderChannel;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderStatus;
import com.davidcreate.jobhub.notification.domain.port.out.CustomReminderRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class CustomReminderPanacheRepository
        implements CustomReminderRepository, PanacheRepositoryBase<CustomReminderEntity, UUID> {

    private final CustomReminderEntityMapper mapper;

    public CustomReminderPanacheRepository(CustomReminderEntityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public CustomReminder save(CustomReminder reminder) {
        CustomReminderEntity entity = mapper.toEntity(reminder);
        if (entity.id == null) {
            entity.id = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (entity.createdAt == null) {
            entity.createdAt = now;
        }
        entity.updatedAt = now;
        persist(entity);
        return mapper.toDomain(entity);
    }

    @Override
    @Transactional
    public CustomReminder update(CustomReminder reminder) {
        CustomReminderEntity entity = findById(reminder.getId());
        if (entity == null) {
            throw new IllegalStateException("Custom reminder not found for update: " + reminder.getId());
        }
        entity.title = reminder.getTitle();
        entity.note = reminder.getNote();
        entity.triggerAtUtc = reminder.getTriggerAtUtc().atOffset(ZoneOffset.UTC);
        entity.channels = reminder.getChannels().stream().map(Enum::name).collect(Collectors.joining(","));
        entity.stage = reminder.getStage() != null ? reminder.getStage().name() : null;
        entity.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        return mapper.toDomain(entity);
    }

    @Override
    public Optional<CustomReminder> findByIdForUser(UUID id, UUID userId) {
        return find("id = :id and userId = :userId", Map.of("id", id, "userId", userId))
                .firstResultOptional()
                .map(mapper::toDomain);
    }

    @Override
    public List<CustomReminder> findAllForUser(UUID userId, boolean includeFired) {
        if (includeFired) {
            return find("userId", userId).list().stream().map(mapper::toDomain).toList();
        }
        return find("userId = :userId and status = :status",
                Map.of("userId", userId, "status", CustomReminderStatus.SCHEDULED.name()))
                .list().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<CustomReminder> findAllForUserAndApplication(UUID userId, UUID applicationId, boolean includeFired) {
        if (includeFired) {
            return find("userId = :userId and applicationId = :applicationId",
                    Map.of("userId", userId, "applicationId", applicationId))
                    .list().stream().map(mapper::toDomain).toList();
        }
        return find("userId = :userId and applicationId = :applicationId and status = :status",
                Map.of("userId", userId, "applicationId", applicationId, "status", CustomReminderStatus.SCHEDULED.name()))
                .list().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<CustomReminder> findDue(Instant now, int limit) {
        return find("status = :status and triggerAtUtc <= :now",
                io.quarkus.panache.common.Sort.ascending("triggerAtUtc"),
                Map.of("status", CustomReminderStatus.SCHEDULED.name(), "now", now.atOffset(ZoneOffset.UTC)))
                .page(0, limit)
                .list().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean markCancelled(UUID id) {
        long updated = update("status = :newStatus, updatedAt = :now where id = :id and status = :scheduled",
                Map.of("newStatus", CustomReminderStatus.CANCELLED.name(),
                        "now", OffsetDateTime.now(ZoneOffset.UTC),
                        "id", id,
                        "scheduled", CustomReminderStatus.SCHEDULED.name()));
        return updated > 0;
    }

    @Override
    @Transactional
    public boolean markFired(UUID id, Set<CustomReminderChannel> channelsFired, Instant firedAtUtc) {
        String joined = channelsFired == null || channelsFired.isEmpty()
                ? ""
                : channelsFired.stream().map(Enum::name).collect(Collectors.joining(","));
        long updated = update("status = :newStatus, channelsFired = :channelsFired, firedAtUtc = :firedAt, updatedAt = :now where id = :id and status = :scheduled",
                Map.of("newStatus", CustomReminderStatus.FIRED.name(),
                        "channelsFired", joined,
                        "firedAt", firedAtUtc.atOffset(ZoneOffset.UTC),
                        "now", OffsetDateTime.now(ZoneOffset.UTC),
                        "id", id,
                        "scheduled", CustomReminderStatus.SCHEDULED.name()));
        return updated > 0;
    }
}
