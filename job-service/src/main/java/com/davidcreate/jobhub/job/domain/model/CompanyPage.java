package com.davidcreate.jobhub.job.domain.model;

import java.util.List;

/**
 * A page of {@link Company} results plus the total match count across every page
 * (story #430, ADR 0025 D3's {@code X-Total-Count} convention).
 */
public record CompanyPage(List<Company> content, long totalCount) {
}
