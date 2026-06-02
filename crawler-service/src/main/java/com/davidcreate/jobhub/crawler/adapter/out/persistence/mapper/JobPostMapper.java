package com.davidcreate.jobhub.crawler.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.JobPostEntity;
import com.davidcreate.jobhub.crawler.domain.model.JobPost;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class JobPostMapper {

    public JobPost toDomain(JobPostEntity entity) {
        return JobPost.builder()
                .id(entity.id)
                .targetId(entity.targetId)
                .title(entity.title)
                .url(entity.url)
                .description(entity.description)
                .contentHash(entity.contentHash)
                .city(entity.city)
                .country(entity.country)
                .compensationMin(entity.compensationMin)
                .compensationMax(entity.compensationMax)
                .employmentType(entity.employmentType)
                .languages(entity.languages)
                .requirements(entity.requirements)
                .firstSeenAt(entity.firstSeenAt)
                .lastSeenAt(entity.lastSeenAt)
                .build();
    }

    public JobPostEntity toEntity(JobPost domain) {
        JobPostEntity entity = new JobPostEntity();
        entity.targetId = domain.getTargetId();
        entity.title = domain.getTitle();
        entity.url = domain.getUrl();
        entity.description = domain.getDescription();
        entity.contentHash = domain.getContentHash();
        entity.city = domain.getCity();
        entity.country = domain.getCountry();
        entity.compensationMin = domain.getCompensationMin();
        entity.compensationMax = domain.getCompensationMax();
        entity.employmentType = domain.getEmploymentType();
        entity.languages = domain.getLanguages();
        entity.requirements = domain.getRequirements();
        entity.firstSeenAt = domain.getFirstSeenAt();
        entity.lastSeenAt = domain.getLastSeenAt();
        return entity;
    }

    public void updateEntity(JobPostEntity entity, JobPost domain) {
        entity.lastSeenAt = domain.getLastSeenAt();
    }
}
