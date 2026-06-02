package com.davidcreate.jobhub.job.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.job.adapter.out.persistence.entity.JobPostEntity;
import com.davidcreate.jobhub.job.adapter.out.persistence.entity.PullTargetEntity;
import com.davidcreate.jobhub.job.domain.model.Company;
import com.davidcreate.jobhub.job.domain.model.EmploymentType;
import com.davidcreate.jobhub.job.domain.model.JobPost;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class JobPostMapper {

    public JobPost toDomain(JobPostEntity entity) {
        PullTargetEntity target = entity.target;
        Company company = target == null ? null : Company.builder()
                .name(target.companyName)
                .logoUrl(target.companyLogoUrl)
                .build();

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
                .employmentType(EmploymentType.fromValue(entity.employmentType))
                .languages(entity.languages)
                .requirements(entity.requirements)
                .firstSeenAt(entity.firstSeenAt)
                .lastSeenAt(entity.lastSeenAt)
                .company(company)
                .source(target == null ? null : target.sourceType)
                .build();
    }
}
