package com.davidcreate.jobhub.job.adapter.in.rest.dto;

import com.davidcreate.jobhub.job.contract.model.CompanyInfo;
import com.davidcreate.jobhub.job.contract.model.JobPostResponse;
import com.davidcreate.jobhub.job.contract.model.JobSearchPage;
import com.davidcreate.jobhub.job.domain.model.Company;
import com.davidcreate.jobhub.job.domain.model.EmploymentType;
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
                .firstSeenAt(job.getFirstSeenAt())
                .lastSeenAt(job.getLastSeenAt())
                .compensationMin(job.getCompensationMin())
                .compensationMax(job.getCompensationMax())
                .language(job.getLanguages())
                .requirements(job.getRequirements())
                .source(job.getSource());

        response.setCompany(toCompanyInfo(job.getCompany()));
        response.setEmploymentType(toEmploymentTypeEnum(job.getEmploymentType()));
        return response;
    }

    public static JobSearchPage toPage(List<JobPost> jobs, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new JobSearchPage()
                .content(jobs.stream().map(JobPostResponseMapper::toResponse).toList())
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages);
    }

    private static CompanyInfo toCompanyInfo(Company company) {
        if (company == null) {
            return null;
        }
        return new CompanyInfo()
                .name(company.getName())
                .logoUrl(toUri(company.getLogoUrl()));
    }

    private static JobPostResponse.EmploymentTypeEnum toEmploymentTypeEnum(EmploymentType type) {
        if (type == null) {
            return null;
        }
        return JobPostResponse.EmploymentTypeEnum.fromValue(type.value());
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
