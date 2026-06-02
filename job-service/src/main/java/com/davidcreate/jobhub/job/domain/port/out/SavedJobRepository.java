package com.davidcreate.jobhub.job.domain.port.out;

import com.davidcreate.jobhub.job.domain.model.SavedJobView;

import java.util.List;
import java.util.UUID;

public interface SavedJobRepository {

    boolean exists(UUID userId, UUID jobId);

    void add(UUID userId, UUID jobId);

    void remove(UUID userId, UUID jobId);

    /** Bookmarks for the user, most recently saved first, with the live job embedded. */
    List<SavedJobView> listByUser(UUID userId, int page, int size);

    long countByUser(UUID userId);
}
