package com.davidcreate.jobhub.auth.adapter.out.persistence;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.VerificationCodeEntity;
import com.davidcreate.jobhub.auth.adapter.out.persistence.mapper.VerificationCodeMapper;
import com.davidcreate.jobhub.auth.application.port.out.VerificationCodeRepository;
import com.davidcreate.jobhub.auth.domain.entity.VerificationCode;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class VerificationCodePanacheRepository
        implements VerificationCodeRepository, PanacheRepositoryBase<VerificationCodeEntity, UUID> {

    private static final Logger LOG = Logger.getLogger(VerificationCodePanacheRepository.class);

    private final VerificationCodeMapper mapper;

    @Override
    public VerificationCode save(VerificationCode code) {
        VerificationCodeEntity entity;
        if (code.getId() != null && (entity = findById(code.getId())) != null) {
            entity.consumedAt = code.getConsumedAt();
            persistAndFlush(entity);
            LOG.infof("UPDATE auth.verification_code id=%s action=%s consumedAt=%s", entity.id, entity.action,
                    entity.consumedAt);
            return mapper.toDomain(entity);
        }
        entity = mapper.toEntity(code);
        if (entity.createdAt == null) {
            entity.createdAt = OffsetDateTime.now();
        }
        persistAndFlush(entity);
        LOG.infof("INSERT auth.verification_code id=%s userId=%s action=%s", entity.id, entity.userId, entity.action);
        return mapper.toDomain(entity);
    }

    @Override
    public Optional<VerificationCode> findOneById(UUID id) {
        return findByIdOptional(id).map(mapper::toDomain);
    }

    @Override
    public Optional<VerificationCode> findActiveByUserAndAction(UUID userId, VerificationAction action) {
        // Newest unconsumed, unexpired code for (user, action).
        return find(
                "userId = ?1 AND action = ?2 AND consumedAt IS NULL AND expiresAt > ?3 ORDER BY createdAt DESC",
                userId, action.value(), OffsetDateTime.now())
                .firstResultOptional()
                .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void consumeAllActiveByUserAndAction(UUID userId, VerificationAction action) {
        OffsetDateTime now = OffsetDateTime.now();
        List<VerificationCodeEntity> active = list(
                "userId = ?1 AND action = ?2 AND consumedAt IS NULL AND expiresAt > ?3",
                userId, action.value(), now);
        for (VerificationCodeEntity e : active) {
            e.consumedAt = now;
            persist(e);
            LOG.infof("CONSUME auth.verification_code id=%s userId=%s action=%s (invalidated by resend)",
                    e.id, e.userId, e.action);
        }
        if (!active.isEmpty()) {
            flush();
        }
    }
}
