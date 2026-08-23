package com.davidcreate.jobhub.auth.adapter.out.persistence;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.TwoFactorChallengeEntity;
import com.davidcreate.jobhub.auth.adapter.out.persistence.mapper.TwoFactorChallengeMapper;
import com.davidcreate.jobhub.auth.application.port.out.TwoFactorChallengeRepository;
import com.davidcreate.jobhub.auth.domain.entity.TwoFactorChallenge;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class TwoFactorChallengePanacheRepository
        implements TwoFactorChallengeRepository, PanacheRepositoryBase<TwoFactorChallengeEntity, UUID> {

    private static final Logger LOG = Logger.getLogger(TwoFactorChallengePanacheRepository.class);

    private final TwoFactorChallengeMapper mapper;

    @Override
    public TwoFactorChallenge save(TwoFactorChallenge challenge) {
        TwoFactorChallengeEntity existing = challenge.getId() != null
                ? findByIdOptional(challenge.getId()).orElse(null)
                : null;

        if (existing != null) {
            existing.tokenHash = challenge.getTokenHash();
            existing.expiresAt = challenge.getExpiresAt();
            existing.consumedAt = challenge.getConsumedAt();
            persistAndFlush(existing);
            LOG.infof("UPDATE auth.two_factor_challenge id=%s userId=%s consumed=%s", existing.id, existing.userId,
                    existing.consumedAt != null);
            return mapper.toDomain(existing);
        }

        TwoFactorChallengeEntity entity = mapper.toEntity(challenge);
        if (entity.createdAt == null) {
            entity.createdAt = OffsetDateTime.now();
        }
        persistAndFlush(entity);
        LOG.infof("INSERT auth.two_factor_challenge id=%s userId=%s", entity.id, entity.userId);
        return mapper.toDomain(entity);
    }

    @Override
    public Optional<TwoFactorChallenge> findOneById(UUID id) {
        return findByIdOptional(id).map(mapper::toDomain);
    }
}
