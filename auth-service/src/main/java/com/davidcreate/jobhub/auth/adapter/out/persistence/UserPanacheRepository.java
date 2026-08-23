package com.davidcreate.jobhub.auth.adapter.out.persistence;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.UserEntity;
import com.davidcreate.jobhub.auth.adapter.out.persistence.mapper.UserMapper;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.domain.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class UserPanacheRepository
        implements UserRepository, PanacheRepositoryBase<UserEntity, UUID> {

    private static final Logger LOG = Logger.getLogger(UserPanacheRepository.class);

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
    public List<User> findByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return find("id in ?1", List.copyOf(ids)).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public User save(User user) {
        if (user.getId() != null) {
            UserEntity existing = findByIdOptional(user.getId()).orElse(null);
            if (existing != null) {
                mapper.updateEntity(existing, user);
                persistAndFlush(existing);
                LOG.infof("UPDATE auth.user id=%s email=%s emailVerified=%s", existing.id, existing.email,
                        existing.emailVerified);
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
        LOG.infof("INSERT auth.user id=%s email=%s emailVerified=%s", entity.id, entity.email, entity.emailVerified);
        return mapper.toDomain(entity);
    }

    @Override
    public void removeById(UUID id) {
        long deleted = delete("id", id);
        LOG.infof("DELETE auth.user id=%s -> %d row(s)", id, deleted);
    }

    @Override
    public List<UUID> findUserIdsWithoutTwoFactorSince(OffsetDateTime since) {
        return getEntityManager()
                .createQuery("select u.id from UserEntity u where u.twoFactorEnabled = false and u.createdAt <= :since",
                        UUID.class)
                .setParameter("since", since)
                .getResultList();
    }
}
