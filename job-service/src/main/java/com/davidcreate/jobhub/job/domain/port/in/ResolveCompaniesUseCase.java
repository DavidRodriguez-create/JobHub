package com.davidcreate.jobhub.job.domain.port.in;

public interface ResolveCompaniesUseCase {

    /**
     * Resolves one batch of pending pull targets ({@code company_id IS NULL}) to a
     * stored {@code crawler.company} row, creating the row if needed. Idempotent: a
     * target already resolved, or a target whose name yields no slug, is never
     * revisited/retried in a way that duplicates or corrupts data.
     *
     * @return the number of pull targets resolved in this batch
     */
    int resolvePending();
}
