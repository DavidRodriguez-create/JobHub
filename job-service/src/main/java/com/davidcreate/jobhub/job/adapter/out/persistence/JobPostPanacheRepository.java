package com.davidcreate.jobhub.job.adapter.out.persistence;

import com.davidcreate.jobhub.job.adapter.out.persistence.entity.JobPostEntity;
import com.davidcreate.jobhub.job.adapter.out.persistence.mapper.JobPostMapper;
import com.davidcreate.jobhub.job.domain.model.EmploymentType;
import com.davidcreate.jobhub.job.domain.model.FacetValue;
import com.davidcreate.jobhub.job.domain.model.JobFacets;
import com.davidcreate.jobhub.job.domain.model.JobPost;
import com.davidcreate.jobhub.job.domain.model.JobSearchQuery;
import com.davidcreate.jobhub.job.domain.model.JobSortOrder;
import com.davidcreate.jobhub.job.domain.model.PostedWithin;
import com.davidcreate.jobhub.job.domain.port.out.JobPostRepository;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JobPostPanacheRepository
        implements JobPostRepository, PanacheRepositoryBase<JobPostEntity, UUID> {

    private static final String REMOTE = "Remote";

    private final JobPostMapper mapper;
    private final EntityManager em;

    public JobPostPanacheRepository(JobPostMapper mapper, EntityManager em) {
        this.mapper = mapper;
        this.em = em;
    }

    @Override
    public Optional<JobPost> findJobById(UUID id) {
        return findByIdOptional(id).map(mapper::toDomain);
    }

    @Override
    public List<JobPost> search(JobSearchQuery query) {
        StringBuilder jpql = new StringBuilder(
                "SELECT j FROM JobPostEntity j LEFT JOIN FETCH j.target t WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        appendFilters(jpql, params, query);
        jpql.append(orderBy(query.sortOrDefault()));

        var typed = em.createQuery(jpql.toString(), JobPostEntity.class);
        params.forEach(typed::setParameter);
        typed.setFirstResult(query.getPage() * query.getSize());
        typed.setMaxResults(query.getSize());

        return typed.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    public long count(JobSearchQuery query) {
        StringBuilder jpql = new StringBuilder(
                "SELECT COUNT(j) FROM JobPostEntity j LEFT JOIN j.target t WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        appendFilters(jpql, params, query);

        var typed = em.createQuery(jpql.toString(), Long.class);
        params.forEach(typed::setParameter);
        return typed.getSingleResult();
    }

    @Override
    public JobFacets facets() {
        List<FacetValue> companies = facetQuery(
                "SELECT t.companyName, COUNT(j) FROM JobPostEntity j JOIN j.target t "
                        + "WHERE t.companyName IS NOT NULL GROUP BY t.companyName ORDER BY t.companyName");

        List<FacetValue> employmentTypes = facetQuery(
                "SELECT j.employmentType, COUNT(j) FROM JobPostEntity j "
                        + "WHERE j.employmentType IS NOT NULL GROUP BY j.employmentType ORDER BY j.employmentType");

        List<FacetValue> locations = locationFacets();
        List<FacetValue> languages = languageFacets();

        Object[] comp = (Object[]) em.createQuery(
                "SELECT MIN(j.compensationMin), MAX(j.compensationMax) FROM JobPostEntity j").getSingleResult();
        Integer compMin = comp[0] == null ? null : ((Number) comp[0]).intValue();
        Integer compMax = comp[1] == null ? null : ((Number) comp[1]).intValue();

        return new JobFacets(companies, locations, languages, employmentTypes, compMin, compMax);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private List<FacetValue> facetQuery(String jpql) {
        return em.createQuery(jpql, Object[].class).getResultList().stream()
                .map(r -> new FacetValue((String) r[0], ((Number) r[1]).longValue()))
                .toList();
    }

    // Distinct countries (excluding the special "remote" marker) plus a synthetic
    // "Remote" entry counting postings whose city or country is "remote" — mirroring
    // how the location search filter treats "Remote".
    private List<FacetValue> locationFacets() {
        List<FacetValue> locations = new ArrayList<>(facetQuery(
                "SELECT j.country, COUNT(j) FROM JobPostEntity j "
                        + "WHERE j.country IS NOT NULL AND LOWER(j.country) <> 'remote' "
                        + "GROUP BY j.country ORDER BY j.country"));
        long remote = em.createQuery(
                "SELECT COUNT(j) FROM JobPostEntity j WHERE LOWER(j.city) = 'remote' OR LOWER(j.country) = 'remote'",
                Long.class).getSingleResult();
        if (remote > 0) {
            locations.add(new FacetValue(REMOTE, remote));
        }
        return locations;
    }

    // languages is a text[] column — unnest it (native SQL; JPQL has no array unnest).
    @SuppressWarnings("unchecked")
    private List<FacetValue> languageFacets() {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT lang, COUNT(*) FROM crawler.job_post, unnest(languages) AS lang "
                        + "GROUP BY lang ORDER BY lang").getResultList();
        return rows.stream()
                .map(r -> new FacetValue((String) r[0], ((Number) r[1]).longValue()))
                .toList();
    }

    private void appendFilters(StringBuilder jpql, Map<String, Object> params, JobSearchQuery query) {
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            jpql.append(" AND (LOWER(j.title) LIKE :keyword OR LOWER(j.description) LIKE :keyword)");
            params.put("keyword", "%" + query.getKeyword().toLowerCase() + "%");
        }

        if (query.getLocations() != null && !query.getLocations().isEmpty()) {
            jpql.append(" AND (");
            boolean first = true;
            int i = 0;
            for (String loc : query.getLocations()) {
                if (loc == null || loc.isBlank()) {
                    continue;
                }
                if (!first) {
                    jpql.append(" OR ");
                }
                first = false;
                if (REMOTE.equalsIgnoreCase(loc.trim())) {
                    jpql.append("LOWER(j.city) = 'remote' OR LOWER(j.country) = 'remote'");
                    continue;
                }
                String[] parts = loc.split(",", 2);
                if (parts.length == 2) {
                    String cityKey = "loc_city_" + i;
                    String countryKey = "loc_country_" + i;
                    jpql.append("(LOWER(j.city) = :").append(cityKey)
                            .append(" AND LOWER(j.country) = :").append(countryKey).append(")");
                    params.put(cityKey, parts[0].trim().toLowerCase());
                    params.put(countryKey, parts[1].trim().toLowerCase());
                } else {
                    String key = "loc_" + i;
                    jpql.append("(LOWER(j.city) = :").append(key)
                            .append(" OR LOWER(j.country) = :").append(key).append(")");
                    params.put(key, loc.trim().toLowerCase());
                }
                i++;
            }
            if (first) {
                jpql.append("1=1");
            }
            jpql.append(")");
        }

        if (query.getCompanies() != null && !query.getCompanies().isEmpty()) {
            jpql.append(" AND LOWER(t.companyName) IN :companies");
            params.put("companies", query.getCompanies().stream().map(String::toLowerCase).toList());
        }

        if (query.getEmploymentTypes() != null && !query.getEmploymentTypes().isEmpty()) {
            jpql.append(" AND j.employmentType IN :employmentTypes");
            params.put("employmentTypes",
                    query.getEmploymentTypes().stream().map(EmploymentType::value).toList());
        }

        if (query.getCompensationMin() != null) {
            jpql.append(" AND j.compensationMin >= :compMin");
            params.put("compMin", query.getCompensationMin());
        }
        if (query.getCompensationMax() != null) {
            jpql.append(" AND (j.compensationMax IS NULL OR j.compensationMax <= :compMax)");
            params.put("compMax", query.getCompensationMax());
        }

        PostedWithin posted = query.getPostedWithin();
        if (posted != null) {
            jpql.append(" AND j.firstSeenAt >= :postedSince");
            params.put("postedSince", OffsetDateTime.now().minus(posted.window()));
        }

        if (query.getLanguages() != null && !query.getLanguages().isEmpty()) {
            jpql.append(" AND array_overlaps(j.languages, :languages) = true");
            params.put("languages", query.getLanguages().toArray(new String[0]));
        }
    }

    private String orderBy(JobSortOrder sort) {
        return switch (sort) {
            case NEWEST -> " ORDER BY j.firstSeenAt DESC";
            case OLDEST -> " ORDER BY j.firstSeenAt ASC";
            case SALARY_DESC -> " ORDER BY j.compensationMin DESC NULLS LAST, j.firstSeenAt DESC";
            case SALARY_ASC -> " ORDER BY j.compensationMin ASC NULLS LAST, j.firstSeenAt DESC";
        };
    }
}
