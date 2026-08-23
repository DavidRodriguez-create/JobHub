package com.davidcreate.jobhub.job.domain.service;

import com.davidcreate.jobhub.job.domain.model.Company;
import com.davidcreate.jobhub.job.domain.model.CompanySlug;
import com.davidcreate.jobhub.job.domain.model.UnresolvedTarget;
import com.davidcreate.jobhub.job.domain.port.in.ResolveCompaniesUseCase;
import com.davidcreate.jobhub.job.domain.port.out.CompanyRepository;
import com.davidcreate.jobhub.job.domain.port.out.PullTargetRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Story #428 (ADR 0023 D5): batched, idempotent reconciler that resolves
 * {@code crawler.pull_target} rows whose {@code company_id} is still {@code NULL} to a
 * stored {@code crawler.company} row, off the read path (no writes during a GET).
 *
 * <p>Story #429 (revised ADR 0024): a brand-new company is inserted with a {@code null}
 * {@code logoUrl}. Logos are curated (hardcoded to each company's own-site icon in
 * {@code db/init/052}, and hand-filled by an admin under #430); they are never derived or
 * fetched here. A new company therefore renders the UI initials chip until it is curated,
 * and is a candidate for the "sparse company" admin alert (#431).
 */
@ApplicationScoped
public class CompanyResolutionService implements ResolveCompaniesUseCase {

    private static final Logger LOG = Logger.getLogger(CompanyResolutionService.class);

    private final CompanyRepository companyRepository;
    private final PullTargetRepository pullTargetRepository;
    private final int batchSize;

    public CompanyResolutionService(
            CompanyRepository companyRepository,
            PullTargetRepository pullTargetRepository,
            @ConfigProperty(name = "job.company.resolve.batch-size", defaultValue = "200") int batchSize) {
        this.companyRepository = companyRepository;
        this.pullTargetRepository = pullTargetRepository;
        this.batchSize = batchSize;
    }

    @Override
    @Transactional
    public int resolvePending() {
        List<UnresolvedTarget> pending = pullTargetRepository.findWithoutCompany(batchSize);
        int resolved = 0;

        for (UnresolvedTarget target : pending) {
            Optional<String> slug = CompanySlug.of(target.getCompanyName());
            if (slug.isEmpty()) {
                LOG.warnf("Pull target %s ('%s') produced no slug; left unresolved, "
                                + "falling back to the crawl-time name/logo",
                        target.getId(), target.getCompanyName());
                continue;
            }

            // Story #429 (revised ADR 0024): a genuine insert is born with a null logo_url.
            // Logos are curated (db/init/052 own-site icons, admin edits under #430), never
            // derived here. The insert-only upsertBySlug port stays the structural guard that
            // a lookup hit (manually edited or not) can never overwrite an existing logo_url.
            Company company = companyRepository.findBySlug(slug.get())
                    .orElseGet(() -> companyRepository.upsertBySlug(Company.builder()
                            .slug(slug.get())
                            .name(target.getCompanyName())
                            .logoUrl(null)
                            .build()));

            pullTargetRepository.assignCompany(target.getId(), company.getId());
            resolved++;
        }

        return resolved;
    }
}
