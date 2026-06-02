package com.davidcreate.jobhub.auth.adapter.out.persistence;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.VerificationCodeEntity;
import com.davidcreate.jobhub.auth.adapter.out.persistence.mapper.VerificationCodeMapper;
import com.davidcreate.jobhub.auth.application.port.out.VerificationCodeRepository;
import com.davidcreate.jobhub.auth.domain.entity.VerificationCode;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class VerificationCodePanacheRepository
        implements VerificationCodeRepository, PanacheRepositoryBase<VerificationCodeEntity, UUID> {

    private final VerificationCodeMapper mapper;

    @Override
    public VerificationCode save(VerificationCode code) {
        VerificationCodeEntity entity;
        if (code.getId() != null && (entity = findById(code.getId())) != null) {
            entity.consumedAt = code.getConsumedAt();
            persistAndFlush(entity);
            return mapper.toDomain(entity);
        }
        entity = mapper.toEntity(code);
        if (entity.createdAt == null) {
            entity.createdAt = OffsetDateTime.now();
        }
        persistAndFlush(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public Optional<VerificationCode> findOneById(UUID id) {
        return findByIdOptional(id).map(mapper::toDomain);
    }
}
