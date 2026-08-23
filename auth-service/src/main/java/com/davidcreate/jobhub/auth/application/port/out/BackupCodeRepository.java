package com.davidcreate.jobhub.auth.application.port.out;

import com.davidcreate.jobhub.auth.domain.entity.BackupCode;

import java.util.List;
import java.util.UUID;

public interface BackupCodeRepository {

    List<BackupCode> saveAll(UUID totpSecretId, List<String> rawCodes);

    List<BackupCode> findByTotpSecretId(UUID totpSecretId);

    BackupCode save(BackupCode code);

    void removeAllByTotpSecretId(UUID totpSecretId);
}
