package com.davidcreate.jobhub.crawler.adapter.out.persistence;

import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.JobPostEntity;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.mapper.JobPostMapper;
import com.davidcreate.jobhub.crawler.domain.model.JobPost;
import com.davidcreate.jobhub.crawler.domain.port.out.JobPostRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class JobPostPanacheRepository implements JobPostRepository, PanacheRepositoryBase<JobPostEntity, UUID> {

    private final JobPostMapper mapper;

    @Override
    public Optional<JobPost> findByContentHash(String contentHash) {
        return find("contentHash", contentHash)
                .firstResultOptional()
                .map(mapper::toDomain);
    }

    @Override
    public void save(JobPost domain) {
        find("contentHash", domain.getContentHash())
                .firstResultOptional()
                .ifPresentOrElse(
                        entity -> {
                            mapper.updateEntity(entity, domain);
                            persistAndFlush(entity);
                        },
                        () -> persistAndFlush(mapper.toEntity(domain)));
    }

    @Override
    public void saveAll(List<JobPost> jobs) {
        jobs.stream()
                .map(mapper::toEntity)
                .forEach(this::persist);
        flush();
    }
}