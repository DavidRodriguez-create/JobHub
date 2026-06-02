package com.davidcreate.jobhub.job.adapter.out.persistence;

import com.davidcreate.jobhub.job.adapter.out.persistence.entity.JobPostEntity;
import com.davidcreate.jobhub.job.adapter.out.persistence.entity.SavedJobEntity;
import com.davidcreate.jobhub.job.adapter.out.persistence.mapper.JobPostMapper;
import com.davidcreate.jobhub.job.domain.model.JobPost;
import com.davidcreate.jobhub.job.domain.model.SavedJobView;
import com.davidcreate.jobhub.job.domain.port.out.SavedJobRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class SavedJobPanacheRepository
        implements SavedJobRepository, PanacheRepositoryBase<SavedJobEntity, UUID> {

    private final JobPostMapper jobPostMapper;
    private final EntityManager em;

    public SavedJobPanacheRepository(JobPostMapper jobPostMapper, EntityManager em) {
        this.jobPostMapper = jobPostMapper;
        this.em = em;
    }

    @Override
    public boolean exists(UUID userId, UUID jobId) {
        return count("userId = ?1 and jobId = ?2", userId, jobId) > 0;
    }

    @Override
    public void add(UUID userId, UUID jobId) {
        SavedJobEntity e = new SavedJobEntity();
        e.userId = userId;
        e.jobId = jobId;
        e.savedAt = OffsetDateTime.now();
        persist(e);
    }

    @Override
    public void remove(UUID userId, UUID jobId) {
        delete("userId = ?1 and jobId = ?2", userId, jobId);
    }

    @Override
    public List<SavedJobView> listByUser(UUID userId, int page, int size) {
        List<SavedJobEntity> saved = find("userId", Sort.descending("savedAt"), userId)
                .page(Page.of(page, size))
                .list();
        if (saved.isEmpty()) {
            return List.of();
        }

        List<UUID> jobIds = saved.stream().map(s -> s.jobId).toList();
        Map<UUID, JobPost> jobsById = em.createQuery(
                        "SELECT j FROM JobPostEntity j LEFT JOIN FETCH j.target WHERE j.id IN :ids",
                        JobPostEntity.class)
                .setParameter("ids", jobIds)
                .getResultList()
                .stream()
                .map(jobPostMapper::toDomain)
                .collect(Collectors.toMap(JobPost::getId, Function.identity()));

        return saved.stream()
                .filter(s -> jobsById.containsKey(s.jobId))
                .map(s -> new SavedJobView(s.savedAt, jobsById.get(s.jobId)))
                .toList();
    }

    @Override
    public long countByUser(UUID userId) {
        return count("userId", userId);
    }
}
