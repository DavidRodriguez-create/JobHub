package com.davidcreate.jobhub.job.domain.port.in;

import com.davidcreate.jobhub.job.domain.model.JobPost;

import java.util.Optional;
import java.util.UUID;

public interface GetJobUseCase {
    Optional<JobPost> getById(UUID id);
}