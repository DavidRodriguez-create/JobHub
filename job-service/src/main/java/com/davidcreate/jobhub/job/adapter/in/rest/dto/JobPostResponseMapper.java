package com.davidcreate.jobhub.job.adapter.in.rest.dto;

import com.davidcreate.jobhub.job.contract.model.CompanyInfo;
import com.davidcreate.jobhub.job.contract.model.JobLocation;
import com.davidcreate.jobhub.job.contract.model.JobPostResponse;
import com.davidcreate.jobhub.job.contract.model.JobSearchPage;
import com.davidcreate.jobhub.job.domain.model.CareerLevel;
import com.davidcreate.jobhub.job.domain.model.Company;
import com.davidcreate.jobhub.job.domain.model.EmploymentType;
import com.davidcreate.jobhub.job.domain.model.JobCount;
import com.davidcreate.jobhub.job.domain.model.JobPost;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public final class JobPostResponseMapper {

    private JobPostResponseMapper() {}

    public static JobPostResponse toResponse(JobPost job) {
        JobPostResponse response = new JobPostResponse()
                .id(job.getId())
                .title(job.getTitle())
                .url(toUri(job.getUrl()))
                .description(job.getDescription())
                .location(job.location())
                .locations(toLocations(job))
                .firstSeenAt(job.getFirstSeenAt())
                .lastSeenAt(job.getLastSeenAt())
                .compensationMin(job.getCompensationMin())
                .compensationMax(job.getCompensationMax())
                .language(job.getLanguages())
                .requirements(job.getRequirements())
                .source(job.getSource());

        response.setCompany(toCompanyInfo(job.getCompany()));
        response.setEmploymentType(toEmploymentTypeEnum(job.getEmploymentType()));
        response.setCareerLevel(toCareerLevelEnum(job.getCareerLevel()));
        return response;
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
     * @param count the hybrid-strategy total (ADR 0018): {@code value} feeds
     *              {@code totalElements}/{@code totalPages} as before; {@code estimate}
     *              is surfaced additively as {@code countIsEstimate} (absent/false
     *              behaviour is unaffected, it is simply always explicit here).
     */
    public static JobSearchPage toPage(List<JobPost> jobs, int page, int size, JobCount count) {
        long totalElements = count.value();
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new JobSearchPage()
                .content(jobs.stream().map(JobPostSummaryMapper::toSummary).toList())
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .countIsEstimate(count.estimate());
    }

    /**
     * Story #428 (ADR 0023 D4): the detail endpoint's projection - every property is
     * populated, including {@code description} (the list projection leaves it null, see
     * {@link JobPostSummaryMapper#toCompanyInfo}). Unknown/blank values serialise as
     * {@code null}, never {@code ""}: {@code name} is exempt (structurally guaranteed
     * non-blank by the DB's NOT NULL constraints on both the resolved and fallback paths).
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
                .description(blankToNull(company.getDescription()))
                .tags(company.getTags())
                .manuallyEdited(company.getManuallyEdited())
                .updatedAt(company.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static JobPostResponse.EmploymentTypeEnum toEmploymentTypeEnum(EmploymentType type) {
        if (type == null) {
            return null;
        }
        return JobPostResponse.EmploymentTypeEnum.fromValue(type.value());
    }

    private static JobPostResponse.CareerLevelEnum toCareerLevelEnum(CareerLevel level) {
        if (level == null) {
            return null;
        }
        return JobPostResponse.CareerLevelEnum.fromValue(level.value());
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
