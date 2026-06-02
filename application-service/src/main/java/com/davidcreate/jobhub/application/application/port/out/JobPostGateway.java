package com.davidcreate.jobhub.application.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface JobPostGateway {

    Optional<JobPostView> findById(UUID jobPostId);

    record JobPostView(
            UUID id,
            String title,
            String url,
            String description,
            String location) {
    }
}
