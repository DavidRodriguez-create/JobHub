package com.davidcreate.jobhub.job.adapter.in.rest.dto;

import com.davidcreate.jobhub.job.contract.model.CompanyInfo;
import com.davidcreate.jobhub.job.contract.model.CompanyUpdateRequest;
import com.davidcreate.jobhub.job.domain.model.Company;
import com.davidcreate.jobhub.job.domain.model.CompanyUpdateCommand;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Story #430 (ADR 0025 D3/D4): maps the admin company read projection ({@link Company} to
 * {@link CompanyInfo}, full projection, {@code description} always populated - the admin
 * screen is never the size-sensitive list projection) and the full-replace edit payload
 * ({@link CompanyUpdateRequest} to {@link CompanyUpdateCommand}). {@code id}/{@code slug}/
 * {@code name} are deliberately never read off {@link CompanyUpdateRequest}: the generated
 * model does not even carry them (ADR 0025 D4), so immutability holds by construction.
 */
public final class CompanyAdminResponseMapper {

    private CompanyAdminResponseMapper() {}

    public static CompanyInfo toResponse(Company company) {
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

    public static CompanyUpdateCommand toCommand(CompanyUpdateRequest request) {
        return CompanyUpdateCommand.builder()
                .website(toStringValue(request.getWebsite()))
                .industry(request.getIndustry())
                .size(request.getSize())
                .headquarters(request.getHeadquarters())
                .description(request.getDescription())
                .tags(request.getTags())
                .logoUrl(toStringValue(request.getLogoUrl()))
                .build();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static String toStringValue(URI uri) {
        return uri == null ? null : uri.toString();
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
