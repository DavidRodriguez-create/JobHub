package com.davidcreate.jobhub.job.adapter.out.persistence;

import com.davidcreate.jobhub.job.adapter.out.persistence.entity.SavedFilterEntity;
import com.davidcreate.jobhub.job.adapter.out.persistence.mapper.SavedFilterMapper;
import com.davidcreate.jobhub.job.domain.model.SavedFilter;
import com.davidcreate.jobhub.job.domain.port.out.SavedFilterRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SavedFilterPanacheRepository
        implements SavedFilterRepository, PanacheRepositoryBase<SavedFilterEntity, UUID> {

    private final SavedFilterMapper mapper;

    public SavedFilterPanacheRepository(SavedFilterMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<SavedFilter> listByUser(UUID userId) {
        return find("userId", Sort.ascending("createdAt"), userId)
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
    public Optional<SavedFilter> findByIdAndUser(UUID id, UUID userId) {
        return find("id = ?1 and userId = ?2", id, userId).firstResultOptional().map(mapper::toDomain);
    }

    @Override
    public SavedFilter save(SavedFilter filter) {
        OffsetDateTime now = OffsetDateTime.now();
        SavedFilterEntity entity;
        if (filter.getId() != null && (entity = findById(filter.getId())) != null) {
            entity.name = filter.getName();
            entity.filters = filter.getFiltersJson();
            entity.updatedAt = now;
            persist(entity);
            return mapper.toDomain(entity);
        }
        entity = mapper.toEntity(filter);
        if (entity.createdAt == null) entity.createdAt = now;
        entity.updatedAt = now;
        persist(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public void removeById(UUID id) {
        delete("id", id);
    }
}
