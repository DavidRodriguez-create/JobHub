package com.davidcreate.jobhub.job.domain.service;

import com.davidcreate.jobhub.job.domain.exception.SavedFilterLimitException;
import com.davidcreate.jobhub.job.domain.exception.SavedFilterNotFoundException;
import com.davidcreate.jobhub.job.domain.model.SavedFilter;
import com.davidcreate.jobhub.job.domain.port.in.SavedFilterUseCase;
import com.davidcreate.jobhub.job.domain.port.out.SavedFilterRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SavedFilterService implements SavedFilterUseCase {

    private final SavedFilterRepository repository;

    public SavedFilterService(SavedFilterRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SavedFilter> list(UUID userId) {
        return repository.listByUser(userId);
    }

    @Override
    @Transactional
    public SavedFilter create(UUID userId, String name, String filtersJson) {
        if (repository.countByUser(userId) >= MAX_PRESETS) {
            throw new SavedFilterLimitException(MAX_PRESETS);
        }
        return repository.save(SavedFilter.builder()
                .userId(userId)
                .name(name.trim())
                .filtersJson(filtersJson)
                .build());
    }

    @Override
    @Transactional
    public SavedFilter update(UUID userId, UUID id, String name, String filtersJson) {
        SavedFilter existing = repository.findByIdAndUser(id, userId)
                .orElseThrow(() -> new SavedFilterNotFoundException(id));
        SavedFilter.SavedFilterBuilder b = existing.toBuilder();
        if (name != null && !name.isBlank()) {
            b.name(name.trim());
        }
        if (filtersJson != null) {
            b.filtersJson(filtersJson);
        }
        return repository.save(b.build());
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID id) {
        SavedFilter existing = repository.findByIdAndUser(id, userId)
                .orElseThrow(() -> new SavedFilterNotFoundException(id));
        repository.removeById(existing.getId());
    }
}
