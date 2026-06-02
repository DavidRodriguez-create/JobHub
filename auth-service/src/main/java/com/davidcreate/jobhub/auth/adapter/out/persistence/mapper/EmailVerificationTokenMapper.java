package com.davidcreate.jobhub.auth.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.EmailVerificationTokenEntity;
import com.davidcreate.jobhub.auth.domain.entity.EmailVerificationToken;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EmailVerificationTokenMapper {

    public EmailVerificationToken toDomain(EmailVerificationTokenEntity e) {
        return EmailVerificationToken.builder()
                .id(e.id)
                .userId(e.userId)
                .token(e.token)
                .expiresAt(e.expiresAt)
                .consumedAt(e.consumedAt)
                .createdAt(e.createdAt)
                .build();
    }

    public EmailVerificationTokenEntity toEntity(EmailVerificationToken t) {
        EmailVerificationTokenEntity e = new EmailVerificationTokenEntity();
        e.id = t.getId();
        e.userId = t.getUserId();
        e.token = t.getToken();
        e.expiresAt = t.getExpiresAt();
        e.consumedAt = t.getConsumedAt();
        e.createdAt = t.getCreatedAt();
        return e;
    }
}
