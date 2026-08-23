package com.davidcreate.jobhub.job.domain.port.out;

import com.davidcreate.jobhub.job.domain.model.Company;
import com.davidcreate.jobhub.job.domain.model.CompanyPage;
import com.davidcreate.jobhub.job.domain.model.CompanySearchQuery;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository {

    Optional<Company> findBySlug(String slug);

    /**
     * Inserts a company row for {@code company.getSlug()} if none exists yet
     * ({@code INSERT ... ON CONFLICT (slug) DO NOTHING}), then re-selects and returns
     * whichever row now exists for that slug: either the one just inserted, or a
     * pre-existing row from a concurrent resolver run (ADR 0023 D3 concurrency rule:
     * select, then insert-on-conflict, then re-select - never select-then-blind-insert).
     */
    Company upsertBySlug(Company company);

    /**
     * Story #430 (ADR 0025 D3): read one stored company by its id, full projection.
     */
    Optional<Company> findCompanyById(UUID id);

    /**
     * Story #430 (ADR 0025 D3): paginated, filtered, sorted browse for the admin
     * enrichment screen. The returned {@link CompanyPage#totalCount()} is the total
     * match count across every page, independent of the requested page size.
     */
    CompanyPage search(CompanySearchQuery query);

    /**
     * Story #430 (ADR 0025 D4): full-replace write of the seven admin-editable fields
     * carried on {@code company} (matched by {@code company.getId()}), in the same
     * statement that also sets {@code source = 'manual'} (persistence-internal, not on
     * the domain model). {@code company.getManuallyEdited()} and
     * {@code company.getUpdatedAt()} are expected to already be {@code true} / "now" -
     * the caller (the admin update service) is responsible for pinning the record and
     * stamping freshness before calling this method, and for confirming the row exists
     * and the edit passed validation first. This is the only write path this repository
     * exposes that can change an existing company's enrichable fields, and it is never
     * called from the crawl reconciler ({@code CompanyResolutionService}, which is
     * insert-only).
     */
    Company update(Company company);
}
