package com.davidcreate.jobhub.application.application.port.out;

import com.davidcreate.jobhub.application.domain.entity.JobPostSnapshot;

import java.util.Optional;
import java.util.UUID;

public interface JobPostSnapshotRepository {

    Optional<JobPostSnapshot> findByContentHash(String contentHash);

    Optional<JobPostSnapshot> findOneById(UUID id);

    JobPostSnapshot save(JobPostSnapshot snapshot);
}
