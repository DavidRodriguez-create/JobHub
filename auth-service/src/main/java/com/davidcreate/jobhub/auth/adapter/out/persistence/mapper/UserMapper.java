package com.davidcreate.jobhub.auth.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.UserEntity;
import com.davidcreate.jobhub.auth.domain.entity.User;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserMapper {

    public User toDomain(UserEntity entity) {
        return User.builder()
                .id(entity.id)
                .firstName(entity.firstName)
                .lastName(entity.lastName)
                .email(entity.email)
                .passwordHash(entity.passwordHash)
                .emailVerified(entity.emailVerified)
                .emailVerifiedAt(entity.emailVerifiedAt)
                .twoFactorEnabled(entity.twoFactorEnabled)
                .createdAt(entity.createdAt)
                .updatedAt(entity.updatedAt)
                .build();
    }

    public UserEntity toEntity(User user) {
        UserEntity entity = new UserEntity();
        entity.id = user.getId();
        entity.firstName = user.getFirstName();
        entity.lastName = user.getLastName();
        entity.email = user.getEmail();
        entity.passwordHash = user.getPasswordHash();
        entity.emailVerified = user.isEmailVerified();
        entity.emailVerifiedAt = user.getEmailVerifiedAt();
        entity.twoFactorEnabled = user.isTwoFactorEnabled();
        entity.createdAt = user.getCreatedAt();
        entity.updatedAt = user.getUpdatedAt();
        return entity;
    }

    public void updateEntity(UserEntity entity, User user) {
        entity.firstName = user.getFirstName();
        entity.lastName = user.getLastName();
        entity.email = user.getEmail();
        entity.passwordHash = user.getPasswordHash();
        entity.emailVerified = user.isEmailVerified();
        entity.emailVerifiedAt = user.getEmailVerifiedAt();
        entity.twoFactorEnabled = user.isTwoFactorEnabled();
    }
}
