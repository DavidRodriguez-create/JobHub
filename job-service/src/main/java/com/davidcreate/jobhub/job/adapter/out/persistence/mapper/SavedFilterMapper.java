package com.davidcreate.jobhub.job.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.job.adapter.out.persistence.entity.SavedFilterEntity;
import com.davidcreate.jobhub.job.domain.model.SavedFilter;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SavedFilterMapper {

    public SavedFilter toDomain(SavedFilterEntity e) {
        return SavedFilter.builder()
                .id(e.id)
                .userId(e.userId)
                .name(e.name)
                .filtersJson(e.filters)
                .createdAt(e.createdAt)
                .updatedAt(e.updatedAt)
                .build();
    }

    public SavedFilterEntity toEntity(SavedFilter f) {
        SavedFilterEntity e = new SavedFilterEntity();
        e.id = f.getId();
        e.userId = f.getUserId();
        e.name = f.getName();
        e.filters = f.getFiltersJson();
        e.createdAt = f.getCreatedAt();
        e.updatedAt = f.getUpdatedAt();
        return e;
    }
}
