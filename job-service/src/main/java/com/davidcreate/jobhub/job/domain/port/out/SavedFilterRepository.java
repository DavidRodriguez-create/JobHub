package com.davidcreate.jobhub.job.domain.port.out;

import com.davidcreate.jobhub.job.domain.model.SavedFilter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedFilterRepository {

    List<SavedFilter> listByUser(UUID userId);

    long countByUser(UUID userId);

    Optional<SavedFilter> findByIdAndUser(UUID id, UUID userId);

    SavedFilter save(SavedFilter filter);

    void removeById(UUID id);
}
