package com.davidcreate.jobhub.crawler.domain.port.out;

import com.davidcreate.jobhub.crawler.domain.model.JobPost;

import java.util.Optional;

public interface JobPostRepository {

    Optional<JobPost> findByContentHash(String contentHash);

    void save(JobPost jobPost);

    void saveAll(java.util.List<JobPost> jobPosts);
}