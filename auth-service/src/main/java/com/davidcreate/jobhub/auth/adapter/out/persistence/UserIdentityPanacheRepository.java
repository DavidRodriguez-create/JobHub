package com.davidcreate.jobhub.auth.adapter.out.persistence;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.UserIdentityEntity;
import com.davidcreate.jobhub.auth.adapter.out.persistence.mapper.UserIdentityMapper;
import com.davidcreate.jobhub.auth.application.port.out.UserIdentityRepository;
import com.davidcreate.jobhub.auth.domain.entity.UserIdentity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class UserIdentityPanacheRepository
        implements UserIdentityRepository, PanacheRepositoryBase<UserIdentityEntity, UUID> {

    private static final Logger LOG = Logger.getLogger(UserIdentityPanacheRepository.class);

    private final UserIdentityMapper mapper;

    @Override
    public Optional<UserIdentity> findByProviderAndSubject(String provider, String providerUserId) {
        return find("provider = ?1 and providerUserId = ?2", provider, providerUserId)
                .firstResultOptional()
                .map(mapper::toDomain);
    }

    @Override
    public UserIdentity save(UserIdentity identity) {
        UserIdentityEntity entity = mapper.toEntity(identity);
        if (entity.createdAt == null) {
            entity.createdAt = OffsetDateTime.now();
        }
        persistAndFlush(entity);
        LOG.infof("INSERT auth.user_identity id=%s userId=%s provider=%s", entity.id, entity.userId, entity.provider);
        return mapper.toDomain(entity);
    }
}
