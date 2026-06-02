package com.davidcreate.jobhub.job.domain.port.in;

import com.davidcreate.jobhub.job.domain.model.SavedJobView;

import java.util.List;
import java.util.UUID;

public interface SavedJobUseCase {

    /** Idempotently bookmark a job for the user. Throws if the job does not exist. */
    void save(UUID userId, UUID jobId);

    /** Idempotently remove a bookmark; no-op if it was not bookmarked. */
    void unsave(UUID userId, UUID jobId);

    SavedJobsPage list(UUID userId, int page, int size);

    record SavedJobsPage(List<SavedJobView> items, long total) {
    }
}
