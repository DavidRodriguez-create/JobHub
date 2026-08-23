package com.davidcreate.jobhub.auth.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.TwoFactorChallengeEntity;
import com.davidcreate.jobhub.auth.domain.entity.TwoFactorChallenge;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TwoFactorChallengeMapper {

    public TwoFactorChallenge toDomain(TwoFactorChallengeEntity e) {
        return TwoFactorChallenge.builder()
                .id(e.id)
                .userId(e.userId)
                .tokenHash(e.tokenHash)
                .expiresAt(e.expiresAt)
                .consumedAt(e.consumedAt)
                .createdAt(e.createdAt)
                .build();
    }

    public TwoFactorChallengeEntity toEntity(TwoFactorChallenge c) {
        TwoFactorChallengeEntity e = new TwoFactorChallengeEntity();
        e.id = c.getId();
        e.userId = c.getUserId();
        e.tokenHash = c.getTokenHash();
        e.expiresAt = c.getExpiresAt();
        e.consumedAt = c.getConsumedAt();
        e.createdAt = c.getCreatedAt();
        return e;
    }
}
