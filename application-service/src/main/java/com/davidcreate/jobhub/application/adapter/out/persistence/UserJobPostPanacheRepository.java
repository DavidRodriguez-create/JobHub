package com.davidcreate.jobhub.application.adapter.out.persistence;

import com.davidcreate.jobhub.application.adapter.out.persistence.entity.UserJobPostEntity;
import com.davidcreate.jobhub.application.adapter.out.persistence.mapper.UserJobPostMapper;
import com.davidcreate.jobhub.application.application.port.out.UserJobPostRepository;
import com.davidcreate.jobhub.application.domain.entity.UserJobPost;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class UserJobPostPanacheRepository
        implements UserJobPostRepository, PanacheRepositoryBase<UserJobPostEntity, UUID> {

    private final UserJobPostMapper mapper;

    @Override
    public Optional<UserJobPost> findOneById(UUID id) {
        return findByIdOptional(id).map(mapper::toDomain);
    }

    @Override
    public List<UserJobPost> listByUser(UUID userId, int page, int size) {
        return find("userId", Sort.descending("createdAt"), userId)
                .page(Page.of(page, size))
                .list()
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long countByUser(UUID userId) {
        return count("userId", userId);
    }

    @Override
    public UserJobPost save(UserJobPost u) {
        UserJobPostEntity entity;
        OffsetDateTime now = OffsetDateTime.now();
        if (u.getId() != null && (entity = findByIdOptional(u.getId()).orElse(null)) != null) {
            mapper.updateEntity(entity, u);
            entity.updatedAt = now;
            persistAndFlush(entity);
            return mapper.toDomain(entity);
        }
        entity = mapper.toEntity(u);
        if (entity.createdAt == null) entity.createdAt = now;
        entity.updatedAt = now;
        persistAndFlush(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public void removeById(UUID id) {
        delete("id", id);
    }

    @Override
    public void removeAllByUser(UUID userId) {
        delete("userId", userId);
    }
}
