package com.davidcreate.jobhub.auth.adapter.out.persistence;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.TotpSecretEntity;
import com.davidcreate.jobhub.auth.adapter.out.persistence.mapper.TotpSecretMapper;
import com.davidcreate.jobhub.auth.application.port.out.TotpSecretRepository;
import com.davidcreate.jobhub.auth.domain.entity.TotpSecret;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class TotpSecretPanacheRepository
        implements TotpSecretRepository, PanacheRepositoryBase<TotpSecretEntity, UUID> {

    private static final Logger LOG = Logger.getLogger(TotpSecretPanacheRepository.class);

    private final TotpSecretMapper mapper;

    @Override
    public TotpSecret save(TotpSecret secret) {
        TotpSecretEntity existing = secret.getId() != null
                ? findByIdOptional(secret.getId()).orElse(null)
                : find("userId", secret.getUserId()).firstResultOptional().orElse(null);

        if (existing != null) {
            existing.encryptedSecret = secret.getEncryptedSecret();
            existing.verified = secret.isVerified();
            existing.verifiedAt = secret.getVerifiedAt();
            persistAndFlush(existing);
            LOG.infof("UPDATE auth.totp_secret id=%s userId=%s verified=%s", existing.id, existing.userId,
                    existing.verified);
            return mapper.toDomain(existing);
        }

        TotpSecretEntity entity = mapper.toEntity(secret);
        if (entity.createdAt == null) {
            entity.createdAt = OffsetDateTime.now();
        }
        persistAndFlush(entity);
        LOG.infof("INSERT auth.totp_secret id=%s userId=%s verified=%s", entity.id, entity.userId, entity.verified);
        return mapper.toDomain(entity);
    }

    @Override
    public Optional<TotpSecret> findByUserId(UUID userId) {
        return find("userId", userId).firstResultOptional().map(mapper::toDomain);
    }

    @Override
    public void removeByUserId(UUID userId) {
        long deleted = delete("userId", userId);
        LOG.infof("DELETE auth.totp_secret userId=%s -> %d row(s)", userId, deleted);
    }
}
