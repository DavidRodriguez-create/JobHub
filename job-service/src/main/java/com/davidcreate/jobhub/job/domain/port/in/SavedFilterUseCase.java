package com.davidcreate.jobhub.job.domain.port.in;

import com.davidcreate.jobhub.job.domain.model.SavedFilter;

import java.util.List;
import java.util.UUID;

public interface SavedFilterUseCase {

    int MAX_PRESETS = 5;

    List<SavedFilter> list(UUID userId);

    SavedFilter create(UUID userId, String name, String filtersJson);

    /** Partial update — null {@code name}/{@code filtersJson} leave that field unchanged. */
    SavedFilter update(UUID userId, UUID id, String name, String filtersJson);

    void delete(UUID userId, UUID id);
}
