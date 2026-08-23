package com.davidcreate.jobhub.job.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.job.adapter.out.persistence.entity.CompanyEntity;
import com.davidcreate.jobhub.job.domain.model.Company;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CompanyMapper {

    public Company toDomain(CompanyEntity entity) {
        if (entity == null) {
            return null;
        }
        return Company.builder()
                .id(entity.id)
                .slug(entity.slug)
                .name(entity.name)
                .website(entity.website)
                .industry(entity.industry)
                .size(entity.size)
                .headquarters(entity.headquarters)
                .description(entity.description)
                .logoUrl(entity.logoUrl)
                .tags(entity.tags)
                .manuallyEdited(entity.manuallyEdited)
                .updatedAt(entity.updatedAt)
                .build();
    }
}
