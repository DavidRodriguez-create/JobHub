package com.davidcreate.jobhub.auth.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.VerificationCodeEntity;
import com.davidcreate.jobhub.auth.domain.entity.VerificationCode;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VerificationCodeMapper {

    public VerificationCode toDomain(VerificationCodeEntity e) {
        return VerificationCode.builder()
                .id(e.id)
                .userId(e.userId)
                .action(VerificationAction.fromValue(e.action))
                .codeHash(e.codeHash)
                .expiresAt(e.expiresAt)
                .consumedAt(e.consumedAt)
                .createdAt(e.createdAt)
                .build();
    }

    public VerificationCodeEntity toEntity(VerificationCode c) {
        VerificationCodeEntity e = new VerificationCodeEntity();
        e.id = c.getId();
        e.userId = c.getUserId();
        e.action = c.getAction().value();
        e.codeHash = c.getCodeHash();
        e.expiresAt = c.getExpiresAt();
        e.consumedAt = c.getConsumedAt();
        e.createdAt = c.getCreatedAt();
        return e;
    }
}
