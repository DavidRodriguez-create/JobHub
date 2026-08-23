package com.davidcreate.jobhub.auth.adapter.out.persistence;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.ApplyProfileEntity;
import com.davidcreate.jobhub.auth.adapter.out.persistence.mapper.ApplyProfileMapper;
import com.davidcreate.jobhub.auth.application.port.out.ApplyProfileRepository;
import com.davidcreate.jobhub.auth.domain.entity.ApplyProfile;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class ApplyProfilePanacheRepository
        implements ApplyProfileRepository, PanacheRepositoryBase<ApplyProfileEntity, UUID> {

    private static final Logger LOG = Logger.getLogger(ApplyProfilePanacheRepository.class);

    private final ApplyProfileMapper mapper;

    @Override
    public Optional<ApplyProfile> findByUserId(UUID userId) {
        return find("userId", userId).firstResultOptional().map(mapper::toDomain);
    }

    @Override
    public ApplyProfile save(ApplyProfile profile) {
        Optional<ApplyProfileEntity> existing = find("userId", profile.getUserId()).firstResultOptional();
        OffsetDateTime now = OffsetDateTime.now();

        if (existing.isPresent()) {
            ApplyProfileEntity entity = existing.get();
            mapper.updateEntity(entity, profile);
            entity.updatedAt = now;
            persistAndFlush(entity);
            LOG.infof("UPDATE auth.apply_profile id=%s userId=%s", entity.id, entity.userId);
            return mapper.toDomain(entity);
        }

        ApplyProfileEntity entity = mapper.toEntity(profile);
        entity.createdAt = now;
        entity.updatedAt = now;
        persistAndFlush(entity);
        LOG.infof("INSERT auth.apply_profile id=%s userId=%s", entity.id, entity.userId);
        return mapper.toDomain(entity);
    }
}
