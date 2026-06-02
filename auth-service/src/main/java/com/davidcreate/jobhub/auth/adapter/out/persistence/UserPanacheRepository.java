package com.davidcreate.jobhub.auth.adapter.out.persistence;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.UserEntity;
import com.davidcreate.jobhub.auth.adapter.out.persistence.mapper.UserMapper;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.domain.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class UserPanacheRepository
        implements UserRepository, PanacheRepositoryBase<UserEntity, UUID> {

    private final UserMapper mapper;

    @Override
    public Optional<User> findUserById(UUID id) {
        return findByIdOptional(id).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return find("email", email).firstResultOptional().map(mapper::toDomain);
    }

    @Override
    public User save(User user) {
        if (user.getId() != null) {
            UserEntity existing = findByIdOptional(user.getId()).orElse(null);
            if (existing != null) {
                mapper.updateEntity(existing, user);
                persistAndFlush(existing);
                return mapper.toDomain(existing);
            }
        }
        UserEntity entity = mapper.toEntity(user);
        OffsetDateTime now = OffsetDateTime.now();
        if (entity.createdAt == null) {
            entity.createdAt = now;
        }
        entity.updatedAt = now;
        persistAndFlush(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public void removeById(UUID id) {
        delete("id", id);
    }
}
