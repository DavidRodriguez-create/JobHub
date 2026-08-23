package com.davidcreate.jobhub.notification.adapter.out.persistence;

import com.davidcreate.jobhub.notification.adapter.out.persistence.entity.DigestRunEntity;
import com.davidcreate.jobhub.notification.adapter.out.persistence.mapper.DigestRunMapper;
import com.davidcreate.jobhub.notification.domain.model.DigestRun;
import com.davidcreate.jobhub.notification.domain.port.out.DigestRunRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

@ApplicationScoped
public class DigestRunPanacheRepository
        implements DigestRunRepository, PanacheRepositoryBase<DigestRunEntity, UUID> {

    private final DigestRunMapper mapper;

    public DigestRunPanacheRepository(DigestRunMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean hasSentThisWeek(UUID userId) {
        Instant startOfIsoWeek = startOfCurrentIsoWeek();
        return count("userId = ?1 and status = ?2 and sentAt >= ?3", userId, "sent", startOfIsoWeek) > 0;
    }

    @Override
    @Transactional
    public DigestRun save(DigestRun digestRun) {
        DigestRunEntity entity = mapper.toEntity(digestRun);
        if (entity.id == null) {
            entity.id = UUID.randomUUID();
        }
        persist(entity);
        return mapper.toDomain(entity);
    }

    private Instant startOfCurrentIsoWeek() {
        return ZonedDateTime.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }
}
