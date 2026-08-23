package com.davidcreate.jobhub.auth.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.BackupCodeEntity;
import com.davidcreate.jobhub.auth.domain.entity.BackupCode;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BackupCodeMapper {

    public BackupCode toDomain(BackupCodeEntity e) {
        return BackupCode.builder()
                .id(e.id)
                .totpSecretId(e.totpSecretId)
                .codeHash(e.codeHash)
                .consumedAt(e.consumedAt)
                .createdAt(e.createdAt)
                .build();
    }

    public BackupCodeEntity toEntity(BackupCode c) {
        BackupCodeEntity e = new BackupCodeEntity();
        e.id = c.getId();
        e.totpSecretId = c.getTotpSecretId();
        e.codeHash = c.getCodeHash();
        e.consumedAt = c.getConsumedAt();
        e.createdAt = c.getCreatedAt();
        return e;
    }
}
