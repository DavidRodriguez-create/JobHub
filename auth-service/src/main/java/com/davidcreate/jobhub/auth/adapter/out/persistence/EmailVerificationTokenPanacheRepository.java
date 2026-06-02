package com.davidcreate.jobhub.auth.adapter.out.persistence;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.EmailVerificationTokenEntity;
import com.davidcreate.jobhub.auth.adapter.out.persistence.mapper.EmailVerificationTokenMapper;
import com.davidcreate.jobhub.auth.application.port.out.EmailVerificationTokenRepository;
import com.davidcreate.jobhub.auth.domain.entity.EmailVerificationToken;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class EmailVerificationTokenPanacheRepository
        implements EmailVerificationTokenRepository, PanacheRepositoryBase<EmailVerificationTokenEntity, UUID> {

    private final EmailVerificationTokenMapper mapper;

    @Override
    public EmailVerificationToken save(EmailVerificationToken token) {
        EmailVerificationTokenEntity entity;
        if (token.getId() != null && (entity = findById(token.getId())) != null) {
            entity.consumedAt = token.getConsumedAt();
            persistAndFlush(entity);
            return mapper.toDomain(entity);
        }
        entity = mapper.toEntity(token);
        if (entity.createdAt == null) {
            entity.createdAt = OffsetDateTime.now();
        }
        persistAndFlush(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public Optional<EmailVerificationToken> findByToken(String token) {
        return find("token", token).firstResultOptional().map(mapper::toDomain);
    }
}
