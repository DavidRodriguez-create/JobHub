package com.davidcreate.jobhub.job.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.job.adapter.out.persistence.entity.JobPostEntity;
import com.davidcreate.jobhub.job.adapter.out.persistence.entity.JobPostLocationEntity;
import com.davidcreate.jobhub.job.adapter.out.persistence.entity.PullTargetEntity;
import com.davidcreate.jobhub.job.domain.model.CareerLevel;
import com.davidcreate.jobhub.job.domain.model.Company;
import com.davidcreate.jobhub.job.domain.model.EmploymentType;
import com.davidcreate.jobhub.job.domain.model.JobLocation;
import com.davidcreate.jobhub.job.domain.model.JobPost;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class JobPostMapper {

    private final CompanyMapper companyMapper;

    public JobPostMapper(CompanyMapper companyMapper) {
        this.companyMapper = companyMapper;
    }

    public JobPost toDomain(JobPostEntity entity) {
        PullTargetEntity target = entity.target;
        Company company = toCompany(target);

        return JobPost.builder()
                .id(entity.id)
                .targetId(entity.targetId)
                .title(entity.title)
                .url(entity.url)
                .description(entity.description)
                .contentHash(entity.contentHash)
                .city(entity.city)
                .country(entity.country)
                .locations(toLocations(entity.locations))
                .compensationMin(entity.compensationMin)
                .compensationMax(entity.compensationMax)
                .employmentType(EmploymentType.fromValue(entity.employmentType))
                .careerLevel(CareerLevel.fromValue(entity.careerLevel))
                .languages(entity.languages)
                .requirements(entity.requirements)
                .firstSeenAt(entity.firstSeenAt)
                .lastSeenAt(entity.lastSeenAt)
                .company(company)
                .source(target == null ? null : target.sourceType)
                .build();
    }

    /**
     * Story #428 (ADR 0023 D5): a resolved target ({@code company_id} set) is mapped
     * entirely from the stored {@link com.davidcreate.jobhub.job.adapter.out.persistence.entity.CompanyEntity}
     * row - the target's own denormalised {@code companyName}/{@code companyLogoUrl} are
     * completely ignored once a company is resolved. An unresolved target (fresh pull
     * target, before the reconciler next runs) falls back to those denormalised columns
     * (name + logo only, every other field null) so a posting never shows with no company
     * at any point in time.
     */
    private Company toCompany(PullTargetEntity target) {
        if (target == null) {
            return null;
        }
        if (target.company != null) {
            return companyMapper.toDomain(target.company);
        }
        return Company.builder()
                .name(target.companyName)
                .logoUrl(target.companyLogoUrl)
                .build();
    }

    /**
     * Guarantees primary-first ordering regardless of the entity collection's insertion
     * order (JPA/DB order is not relied upon, per ADR 0017 / QAE-JOB-RETURN-1).
     * Non-primary entries are then ordered by {@code position}.
     */
    private List<JobLocation> toLocations(List<JobPostLocationEntity> childRows) {
        if (childRows == null || childRows.isEmpty()) {
            return Collections.emptyList();
        }
        return childRows.stream()
                .sorted(Comparator.comparing((JobPostLocationEntity row) -> row.isPrimary)
                        .reversed()
                        .thenComparing(row -> row.position))
                .map(row -> JobLocation.builder()
                        .country(row.country)
                        .city(row.city)
                        .primary(row.isPrimary)
                        .build())
                .toList();
    }
}
