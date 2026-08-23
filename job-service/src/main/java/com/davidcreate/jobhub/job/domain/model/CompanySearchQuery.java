package com.davidcreate.jobhub.job.domain.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Admin browse query for {@code GET /jobs/admin/companies} (story #430, ADR 0025 D3):
 * an optional case-insensitive name substring, an optional {@code manuallyEdited}
 * provenance filter, sort order and zero-based pagination.
 */
@Getter
@Builder
public class CompanySearchQuery {

    private final String q;
    private final Boolean manuallyEdited;

    @Builder.Default
    private final CompanySortOrder sort = CompanySortOrder.NAME_ASC;

    @Builder.Default
    private final int page = 0;

    @Builder.Default
    private final int size = 20;
}
