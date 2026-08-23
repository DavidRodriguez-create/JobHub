package com.davidcreate.jobhub.auth.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.UserIdentityEntity;
import com.davidcreate.jobhub.auth.domain.entity.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserIdentityMapper {

    public UserIdentity toDomain(UserIdentityEntity entity) {
        return UserIdentity.builder()
                .id(entity.id)
                .userId(entity.userId)
                .provider(entity.provider)
                .providerUserId(entity.providerUserId)
                .email(entity.email)
                .createdAt(entity.createdAt)
                .build();
    }

    public UserIdentityEntity toEntity(UserIdentity identity) {
        UserIdentityEntity entity = new UserIdentityEntity();
        entity.id = identity.getId();
        entity.userId = identity.getUserId();
        entity.provider = identity.getProvider();
        entity.providerUserId = identity.getProviderUserId();
        entity.email = identity.getEmail();
        entity.createdAt = identity.getCreatedAt();
        return entity;
    }
}
