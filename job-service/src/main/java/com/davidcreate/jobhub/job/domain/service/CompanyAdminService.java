package com.davidcreate.jobhub.job.domain.service;

import com.davidcreate.jobhub.job.domain.exception.CompanyNotFoundException;
import com.davidcreate.jobhub.job.domain.model.Company;
import com.davidcreate.jobhub.job.domain.model.CompanyPage;
import com.davidcreate.jobhub.job.domain.model.CompanySearchQuery;
import com.davidcreate.jobhub.job.domain.model.CompanyUpdateCommand;
import com.davidcreate.jobhub.job.domain.port.in.AdminCompanyQueryUseCase;
import com.davidcreate.jobhub.job.domain.port.in.UpdateCompanyUseCase;
import com.davidcreate.jobhub.job.domain.port.out.CompanyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Story #430 (ADR 0025): admin read/list/update orchestration for {@code crawler.company}.
 * Deliberately its own service, separate from {@link CompanyResolutionService} (the crawl
 * reconciler, unchanged by this story, insert-only): this class is the ONLY caller of
 * {@link CompanyRepository#update(Company)}, so a full-replace admin edit can never be
 * confused with, or triggered from, the reconciliation pass (ADR 0025 D2).
 */
@ApplicationScoped
public class CompanyAdminService implements AdminCompanyQueryUseCase, UpdateCompanyUseCase {

    private static final Pattern TAG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final int MAX_TAGS = 20;
    private static final int TAG_MAX_LENGTH = 40;
    private static final int WEBSITE_MAX_LENGTH = 2048;
    private static final int LOGO_URL_MAX_LENGTH = 2048;
    private static final int INDUSTRY_MAX_LENGTH = 80;
    private static final int SIZE_MAX_LENGTH = 40;
    private static final int HEADQUARTERS_MAX_LENGTH = 120;
    private static final int DESCRIPTION_MAX_LENGTH = 2000;

    private final CompanyRepository repository;

    public CompanyAdminService(CompanyRepository repository) {
        this.repository = repository;
    }

    @Override
    public CompanyPage list(CompanySearchQuery query) {
        return repository.search(query);
    }

    @Override
    public Company getById(UUID id) {
        return repository.findCompanyById(id)
                .orElseThrow(() -> new CompanyNotFoundException("Company " + id + " not found"));
    }

    /**
     * Existence is checked BEFORE validation (an unknown id is a 404 regardless of body
     * shape, AC-430-21), and validation runs BEFORE any repository write (all-or-nothing,
     * AC-430-36): {@link CompanyRepository#update(Company)} is invoked at most once, and
     * only after both gates pass.
     */
    @Override
    @Transactional
    public Company update(UUID id, CompanyUpdateCommand command) {
        Company existing = repository.findCompanyById(id)
                .orElseThrow(() -> new CompanyNotFoundException("Company " + id + " not found"));

        validateLength("website", command.getWebsite(), WEBSITE_MAX_LENGTH);
        validateLength("industry", command.getIndustry(), INDUSTRY_MAX_LENGTH);
        validateLength("size", command.getSize(), SIZE_MAX_LENGTH);
        validateLength("headquarters", command.getHeadquarters(), HEADQUARTERS_MAX_LENGTH);
        validateLength("description", command.getDescription(), DESCRIPTION_MAX_LENGTH);
        validateLength("logoUrl", command.getLogoUrl(), LOGO_URL_MAX_LENGTH);
        List<String> normalizedTags = validateAndNormalizeTags(command.getTags());

        Company toWrite = Company.builder()
                .id(existing.getId())
                .slug(existing.getSlug())
                .name(existing.getName())
                .website(blankToNull(command.getWebsite()))
                .industry(blankToNull(command.getIndustry()))
                .size(blankToNull(command.getSize()))
                .headquarters(blankToNull(command.getHeadquarters()))
                .description(blankToNull(command.getDescription()))
                .tags(normalizedTags)
                .logoUrl(blankToNull(command.getLogoUrl()))
                .manuallyEdited(true)
                .updatedAt(OffsetDateTime.now())
                .build();

        return repository.update(toWrite);
    }

    private static void validateLength(String field, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new BadRequestException(field + " must be at most " + maxLength + " characters");
        }
    }

    private static List<String> validateAndNormalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        if (tags.size() > MAX_TAGS) {
            throw new BadRequestException("tags must contain at most " + MAX_TAGS + " entries");
        }
        Set<String> seen = new HashSet<>();
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                throw new BadRequestException("tags must not contain blank entries");
            }
            if (tag.length() > TAG_MAX_LENGTH) {
                throw new BadRequestException("each tag must be at most " + TAG_MAX_LENGTH + " characters");
            }
            if (!TAG_PATTERN.matcher(tag).matches()) {
                throw new BadRequestException(
                        "tag '" + tag + "' must be lowercase kebab-case (letters, digits, single hyphens)");
            }
            if (!seen.add(tag)) {
                throw new BadRequestException("duplicate tag: " + tag);
            }
        }
        return tags;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
