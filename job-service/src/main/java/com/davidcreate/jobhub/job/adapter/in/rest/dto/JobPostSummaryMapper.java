package com.davidcreate.jobhub.job.adapter.in.rest.dto;

import com.davidcreate.jobhub.job.contract.model.CompanyInfo;
import com.davidcreate.jobhub.job.contract.model.JobLocation;
import com.davidcreate.jobhub.job.contract.model.JobPostSummary;
import com.davidcreate.jobhub.job.domain.model.CareerLevel;
import com.davidcreate.jobhub.job.domain.model.Company;
import com.davidcreate.jobhub.job.domain.model.EmploymentType;
import com.davidcreate.jobhub.job.domain.model.JobPost;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Maps {@link JobPost} to the slim {@link JobPostSummary} card projection used by
 * {@code GET /jobs} (Story #330). Deliberately excludes description/requirements;
 * the full body is only ever produced by {@link JobPostResponseMapper#toResponse(JobPost)}
 * for {@code GET /jobs/{id}}.
 */
public final class JobPostSummaryMapper {

    private JobPostSummaryMapper() {}

    public static JobPostSummary toSummary(JobPost job) {
        JobPostSummary summary = new JobPostSummary()
                .id(job.getId())
                .title(job.getTitle())
                .url(toUri(job.getUrl()))
                .location(job.location())
                .locations(toLocations(job))
                .firstSeenAt(job.getFirstSeenAt())
                .lastSeenAt(job.getLastSeenAt())
                .compensationMin(job.getCompensationMin())
                .compensationMax(job.getCompensationMax())
                .language(job.getLanguages())
                .source(job.getSource());

        summary.setCompany(toCompanyInfo(job.getCompany()));
        summary.setEmploymentType(toEmploymentTypeEnum(job.getEmploymentType()));
        summary.setCareerLevel(toCareerLevelEnum(job.getCareerLevel()));
        return summary;
    }

    private static List<JobLocation> toLocations(JobPost job) {
        if (job.getLocations() == null || job.getLocations().isEmpty()) {
            return List.of();
        }
        return job.getLocations().stream()
                .map(loc -> new JobLocation()
                        .country(loc.getCountry())
                        .city(loc.getCity())
                        .primary(loc.isPrimary()))
                .toList();
    }

    /**
     * Story #428 (ADR 0023 D4): the list projection - every property is populated
     * identically to {@link JobPostResponseMapper#toCompanyInfo}, EXCEPT
     * {@code description}, which is always {@code null} here (kept off card payloads,
     * mirroring how this class already drops the job's own description/requirements,
     * story #330). Unknown/blank values serialise as {@code null}, never {@code ""}.
     */
    private static CompanyInfo toCompanyInfo(Company company) {
        if (company == null) {
            return null;
        }
        return new CompanyInfo()
                .id(company.getId())
                .slug(company.getSlug())
                .name(company.getName())
                .logoUrl(toUri(blankToNull(company.getLogoUrl())))
                .website(toUri(blankToNull(company.getWebsite())))
                .industry(blankToNull(company.getIndustry()))
                .size(blankToNull(company.getSize()))
                .headquarters(blankToNull(company.getHeadquarters()))
                .description(null)
                .tags(company.getTags())
                .manuallyEdited(company.getManuallyEdited())
                .updatedAt(company.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static JobPostSummary.EmploymentTypeEnum toEmploymentTypeEnum(EmploymentType type) {
        if (type == null) {
            return null;
        }
        return JobPostSummary.EmploymentTypeEnum.fromValue(type.value());
    }

    private static JobPostSummary.CareerLevelEnum toCareerLevelEnum(CareerLevel level) {
        if (level == null) {
            return null;
        }
        return JobPostSummary.CareerLevelEnum.fromValue(level.value());
    }

    private static URI toUri(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
