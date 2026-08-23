package com.davidcreate.jobhub.job.adapter.out.persistence;

import com.davidcreate.jobhub.job.adapter.out.persistence.entity.CompanyEntity;
import com.davidcreate.jobhub.job.adapter.out.persistence.mapper.CompanyMapper;
import com.davidcreate.jobhub.job.domain.model.Company;
import com.davidcreate.jobhub.job.domain.model.CompanyPage;
import com.davidcreate.jobhub.job.domain.model.CompanySearchQuery;
import com.davidcreate.jobhub.job.domain.port.out.CompanyRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CompanyPanacheRepository
        implements CompanyRepository, PanacheRepositoryBase<CompanyEntity, UUID> {

    private final CompanyMapper mapper;
    private final EntityManager em;

    public CompanyPanacheRepository(CompanyMapper mapper, EntityManager em) {
        this.mapper = mapper;
        this.em = em;
    }

    @Override
    public Optional<Company> findBySlug(String slug) {
        return find("slug", slug).firstResultOptional().map(mapper::toDomain);
    }

    /**
     * ADR 0023 D3 concurrency rule: {@code INSERT ... ON CONFLICT (slug) DO NOTHING}, then
     * re-select - never select-then-blind-insert. The caller ({@link
     * com.davidcreate.jobhub.job.domain.service.CompanyResolutionService}) has already tried
     * {@link #findBySlug(String)} and missed; this method performs the insert-on-conflict and
     * the re-select together so a concurrent resolver run can never produce two rows for the
     * same slug.
     */
    @Override
    public Company upsertBySlug(Company company) {
        OffsetDateTime now = OffsetDateTime.now();
        em.createNativeQuery(
                        "INSERT INTO crawler.company"
                                + " (id, slug, name, logo_url, source, manually_edited, created_at, updated_at)"
                                + " VALUES (gen_random_uuid(), ?1, ?2, ?3, 'crawl', false, ?4, ?4)"
                                + " ON CONFLICT (slug) DO NOTHING")
                .setParameter(1, company.getSlug())
                .setParameter(2, company.getName())
                .setParameter(3, company.getLogoUrl())
                .setParameter(4, now)
                .executeUpdate();

        return findBySlug(company.getSlug())
                .orElseThrow(() -> new IllegalStateException(
                        "Company upsert for slug '" + company.getSlug() + "' did not produce a row"));
    }

    /**
     * Story #430 (ADR 0025 D3): read one stored company by its id, full projection.
     *
     * <p>Named {@code findCompanyById}, not {@code findById}: {@link PanacheRepositoryBase}
     * already declares {@code CompanyEntity findById(UUID)}, which is not override-compatible
     * with a {@code CompanyRepository}-declared {@code Optional<Company> findById(UUID)} on
     * the same class (same erasure, unrelated return types).
     */
    @Override
    public Optional<Company> findCompanyById(UUID id) {
        return findByIdOptional(id).map(mapper::toDomain);
    }

    /**
     * Story #430 (ADR 0025 D3): dynamic JPQL built with a {@code StringBuilder} +
     * {@code Map<String,Object>} (CLAUDE.md persistence rule), never string
     * concatenation of the caller-supplied {@code q}/{@code manuallyEdited} values.
     */
    @Override
    public CompanyPage search(CompanySearchQuery query) {
        StringBuilder jpql = new StringBuilder("1=1");
        Map<String, Object> params = new HashMap<>();

        if (query.getQ() != null && !query.getQ().isBlank()) {
            jpql.append(" and lower(name) like :q");
            params.put("q", "%" + query.getQ().toLowerCase() + "%");
        }
        if (query.getManuallyEdited() != null) {
            jpql.append(" and manuallyEdited = :manuallyEdited");
            params.put("manuallyEdited", query.getManuallyEdited());
        }

        PanacheQuery<CompanyEntity> panacheQuery = find(jpql.toString(), sortFor(query), params);
        long total = panacheQuery.count();
        List<Company> content = panacheQuery.page(query.getPage(), query.getSize())
                .list().stream().map(mapper::toDomain).toList();
        return new CompanyPage(content, total);
    }

    private static Sort sortFor(CompanySearchQuery query) {
        return switch (query.getSort()) {
            case NAME_DESC -> Sort.by("name", Sort.Direction.Descending);
            case UPDATED_DESC -> Sort.by("updatedAt", Sort.Direction.Descending);
            case UPDATED_ASC -> Sort.by("updatedAt", Sort.Direction.Ascending);
            case NAME_ASC -> Sort.by("name", Sort.Direction.Ascending);
        };
    }

    /**
     * Story #430 (ADR 0025 D4): full-replace write of the seven admin-editable fields
     * on the managed entity for {@code company.getId()}, plus {@code source = 'manual'}
     * (persistence-internal). Panache's managed-entity dirty checking flushes these
     * field mutations at transaction commit; no explicit {@code persist()} call needed.
     */
    @Override
    public Company update(Company company) {
        CompanyEntity entity = findById(company.getId());
        if (entity == null) {
            throw new IllegalStateException(
                    "Company " + company.getId() + " disappeared during update");
        }
        entity.website = company.getWebsite();
        entity.industry = company.getIndustry();
        entity.size = company.getSize();
        entity.headquarters = company.getHeadquarters();
        entity.description = company.getDescription();
        entity.tags = company.getTags();
        entity.logoUrl = company.getLogoUrl();
        entity.source = "manual";
        entity.manuallyEdited = Boolean.TRUE.equals(company.getManuallyEdited());
        entity.updatedAt = company.getUpdatedAt() != null ? company.getUpdatedAt() : OffsetDateTime.now();
        return mapper.toDomain(entity);
    }
}
