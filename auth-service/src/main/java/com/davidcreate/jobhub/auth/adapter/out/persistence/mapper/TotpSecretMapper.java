package com.davidcreate.jobhub.auth.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.TotpSecretEntity;
import com.davidcreate.jobhub.auth.domain.entity.TotpSecret;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TotpSecretMapper {

    public TotpSecret toDomain(TotpSecretEntity e) {
        return TotpSecret.builder()
                .id(e.id)
                .userId(e.userId)
                .encryptedSecret(e.encryptedSecret)
                .verified(e.verified)
                .verifiedAt(e.verifiedAt)
                .createdAt(e.createdAt)
                .build();
    }

    public TotpSecretEntity toEntity(TotpSecret s) {
        TotpSecretEntity e = new TotpSecretEntity();
        e.id = s.getId();
        e.userId = s.getUserId();
        e.encryptedSecret = s.getEncryptedSecret();
        e.verified = s.isVerified();
        e.verifiedAt = s.getVerifiedAt();
        e.createdAt = s.getCreatedAt();
        return e;
    }
}
