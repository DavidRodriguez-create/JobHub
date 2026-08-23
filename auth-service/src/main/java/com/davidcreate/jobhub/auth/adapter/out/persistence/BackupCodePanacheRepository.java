package com.davidcreate.jobhub.auth.adapter.out.persistence;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.BackupCodeEntity;
import com.davidcreate.jobhub.auth.adapter.out.persistence.mapper.BackupCodeMapper;
import com.davidcreate.jobhub.auth.application.port.out.BackupCodeRepository;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.domain.entity.BackupCode;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class BackupCodePanacheRepository
        implements BackupCodeRepository, PanacheRepositoryBase<BackupCodeEntity, UUID> {

    private static final Logger LOG = Logger.getLogger(BackupCodePanacheRepository.class);

    private final BackupCodeMapper mapper;
    private final PasswordHasher passwordHasher;

    @Override
    public List<BackupCode> saveAll(UUID totpSecretId, List<String> rawCodes) {
        OffsetDateTime now = OffsetDateTime.now();
        List<BackupCode> saved = new ArrayList<>(rawCodes.size());
        for (String rawCode : rawCodes) {
            BackupCodeEntity entity = new BackupCodeEntity();
            entity.totpSecretId = totpSecretId;
            entity.codeHash = passwordHasher.hash(rawCode);
            entity.createdAt = now;
            persist(entity);
            saved.add(mapper.toDomain(entity));
        }
        getEntityManager().flush();
        LOG.infof("INSERT auth.totp_backup_code totpSecretId=%s -> %d row(s)", totpSecretId, saved.size());
        return saved;
    }

    @Override
    public List<BackupCode> findByTotpSecretId(UUID totpSecretId) {
        return find("totpSecretId", totpSecretId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public BackupCode save(BackupCode code) {
        BackupCodeEntity existing = code.getId() != null ? findByIdOptional(code.getId()).orElse(null) : null;

        if (existing != null) {
            existing.consumedAt = code.getConsumedAt();
            persistAndFlush(existing);
            LOG.infof("UPDATE auth.totp_backup_code id=%s consumed=%s", existing.id, existing.consumedAt != null);
            return mapper.toDomain(existing);
        }

        BackupCodeEntity entity = mapper.toEntity(code);
        if (entity.createdAt == null) {
            entity.createdAt = OffsetDateTime.now();
        }
        persistAndFlush(entity);
        LOG.infof("INSERT auth.totp_backup_code id=%s totpSecretId=%s", entity.id, entity.totpSecretId);
        return mapper.toDomain(entity);
    }

    @Override
    public void removeAllByTotpSecretId(UUID totpSecretId) {
        long deleted = delete("totpSecretId", totpSecretId);
        LOG.infof("DELETE auth.totp_backup_code totpSecretId=%s -> %d row(s)", totpSecretId, deleted);
    }
}
