package com.davidcreate.jobhub.application.adapter.out.persistence;

import com.davidcreate.jobhub.application.adapter.out.persistence.entity.JobPostSnapshotEntity;
import com.davidcreate.jobhub.application.adapter.out.persistence.mapper.JobPostSnapshotMapper;
import com.davidcreate.jobhub.application.application.port.out.JobPostSnapshotRepository;
import com.davidcreate.jobhub.application.domain.entity.JobPostSnapshot;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class JobPostSnapshotPanacheRepository
        implements JobPostSnapshotRepository, PanacheRepositoryBase<JobPostSnapshotEntity, UUID> {

    private final JobPostSnapshotMapper mapper;

    @Override
    public Optional<JobPostSnapshot> findByContentHash(String contentHash) {
        return find("contentHash", contentHash).firstResultOptional().map(mapper::toDomain);
    }

    @Override
    public Optional<JobPostSnapshot> findOneById(UUID id) {
        return findByIdOptional(id).map(mapper::toDomain);
    }

    @Override
    public JobPostSnapshot save(JobPostSnapshot s) {
        JobPostSnapshotEntity entity = mapper.toEntity(s);
        if (entity.snapshottedAt == null) {
            entity.snapshottedAt = OffsetDateTime.now();
        }
        persistAndFlush(entity);
        return mapper.toDomain(entity);
    }
}
