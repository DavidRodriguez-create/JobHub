package com.davidcreate.jobhub.job.domain.service;

import com.davidcreate.jobhub.job.domain.exception.JobNotFoundException;
import com.davidcreate.jobhub.job.domain.port.in.SavedJobUseCase;
import com.davidcreate.jobhub.job.domain.port.out.JobPostRepository;
import com.davidcreate.jobhub.job.domain.port.out.SavedJobRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.UUID;

@ApplicationScoped
public class SavedJobService implements SavedJobUseCase {

    private final SavedJobRepository savedJobRepository;
    private final JobPostRepository jobPostRepository;

    public SavedJobService(SavedJobRepository savedJobRepository, JobPostRepository jobPostRepository) {
        this.savedJobRepository = savedJobRepository;
        this.jobPostRepository = jobPostRepository;
    }

    @Override
    @Transactional
    public void save(UUID userId, UUID jobId) {
        if (jobPostRepository.findJobById(jobId).isEmpty()) {
            throw new JobNotFoundException("Job with id " + jobId + " not found");
        }
        if (!savedJobRepository.exists(userId, jobId)) {
            savedJobRepository.add(userId, jobId);
        }
    }

    @Override
    @Transactional
    public void unsave(UUID userId, UUID jobId) {
        savedJobRepository.remove(userId, jobId);
    }

    @Override
    @Transactional
    public SavedJobsPage list(UUID userId, int page, int size) {
        int p = Math.max(0, page);
        int s = size <= 0 ? 20 : Math.min(size, 100);
        return new SavedJobsPage(
                savedJobRepository.listByUser(userId, p, s),
                savedJobRepository.countByUser(userId));
    }
}
