package com.davidcreate.jobhub.job.domain.port.in;

import com.davidcreate.jobhub.job.domain.model.Company;
import com.davidcreate.jobhub.job.domain.model.CompanyPage;
import com.davidcreate.jobhub.job.domain.model.CompanySearchQuery;

import java.util.UUID;

/**
 * Story #430 (ADR 0025 D3): read-side admin company operations backing
 * {@code GET /jobs/admin/companies} and {@code GET /jobs/admin/companies/{id}}.
 */
public interface AdminCompanyQueryUseCase {

    /**
     * Paginated, filtered, sorted browse of every stored company, full projection.
     */
    CompanyPage list(CompanySearchQuery query);

    /**
     * @throws com.davidcreate.jobhub.job.domain.exception.CompanyNotFoundException
     *         when no company exists with this id
     */
    Company getById(UUID id);
}
