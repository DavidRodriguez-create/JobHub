package com.davidcreate.jobhub.job.domain.port.in;

import com.davidcreate.jobhub.job.domain.model.Company;
import com.davidcreate.jobhub.job.domain.model.CompanyUpdateCommand;

import java.util.UUID;

/**
 * Story #430 (ADR 0025 D4/D5): full-replace admin edit of a company's seven enrichable
 * fields, backing {@code PUT /jobs/admin/companies/{id}}. On success the row is pinned
 * ({@code manuallyEdited = true}, {@code source = 'manual'}, a fresh {@code updatedAt}),
 * which is the record-level override that ADR 0025 D2 relies on the reconciler
 * ({@code CompanyResolutionService}) never touching.
 */
public interface UpdateCompanyUseCase {

    /**
     * @throws com.davidcreate.jobhub.job.domain.exception.CompanyNotFoundException
     *         when no company exists with this id (checked before validation)
     * @throws jakarta.ws.rs.BadRequestException
     *         when any field fails validation (ADR 0025 D5); the whole request is rejected
     *         together, no partial write
     */
    Company update(UUID id, CompanyUpdateCommand command);
}
