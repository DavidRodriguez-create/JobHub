package com.davidcreate.jobhub.application.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.application.adapter.out.persistence.entity.JobPostSnapshotEntity;
import com.davidcreate.jobhub.application.domain.entity.JobPostSnapshot;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class JobPostSnapshotMapper {

    public JobPostSnapshot toDomain(JobPostSnapshotEntity e) {
        return JobPostSnapshot.builder()
                .id(e.id)
                .jobPostId(e.jobPostId)
                .contentHash(e.contentHash)
                .title(e.title)
                .company(e.company)
                .url(e.url)
                .location(e.location)
                .snapshottedAt(e.snapshottedAt)
                .build();
    }

    public JobPostSnapshotEntity toEntity(JobPostSnapshot s) {
        JobPostSnapshotEntity e = new JobPostSnapshotEntity();
        e.id = s.getId();
        e.jobPostId = s.getJobPostId();
        e.contentHash = s.getContentHash();
        e.title = s.getTitle();
        e.company = s.getCompany();
        e.url = s.getUrl();
        e.location = s.getLocation();
        e.snapshottedAt = s.getSnapshottedAt();
        return e;
    }
}
