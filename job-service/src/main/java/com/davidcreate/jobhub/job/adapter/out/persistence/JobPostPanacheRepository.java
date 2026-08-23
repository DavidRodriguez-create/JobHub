package com.davidcreate.jobhub.job.adapter.out.persistence;

import com.davidcreate.jobhub.job.adapter.out.persistence.entity.JobPostEntity;
import com.davidcreate.jobhub.job.adapter.out.persistence.mapper.JobPostMapper;
import com.davidcreate.jobhub.job.domain.model.CareerLevel;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class JobPostPanacheRepository
        implements JobPostRepository, PanacheRepositoryBase<JobPostEntity, UUID> {

    /**
     * Identifies a single facet dimension. Used by {@link #appendFiltersExcept} to
     * select which filter clause to omit when computing that dimension's facet group.
     *
     * <p>{@code KEYWORD} and {@code POSTED_WITHIN} are intentionally absent: they are not
     * facet groups themselves and are always applied to every group (they only ever narrow
     * the data and never need to be re-widened by re-selection).
     */
    public enum Dimension {
        LOCATION, LANGUAGE, COMPANY, EMPLOYMENT_TYPE, CAREER_LEVEL, COMPENSATION
    }

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
        // Story #428 (ADR 0023 D2): company reached through j.target.company costs no extra
        // query - one more LEFT JOIN FETCH onto the same statement, same anti-N+1 guarantee
        // as #333/#406.
        StringBuilder jpql = new StringBuilder(
                "SELECT j FROM JobPostEntity j LEFT JOIN FETCH j.target t LEFT JOIN FETCH t.company WHERE 1=1");
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

    /**
     * PostgreSQL planner row estimate for {@code query}'s filter predicates
     * (ADR 0018): {@code EXPLAIN} (plan-only, never {@code EXPLAIN ANALYZE}, no
     * row scan/execution) over the same predicates {@link #count(JobSearchQuery)}
     * would evaluate. JPQL has no {@code EXPLAIN} support through the JPA API, so
     * this runs as a dedicated native-SQL query mirroring {@link #appendFilters}
     * one-to-one with real table/column names (kept next to it, see
     * {@link #appendEstimateFilters}, so the two stay in sync by inspection).
     */
    @Override
    public long estimateCount(JobSearchQuery query) {
        StringBuilder sql = new StringBuilder(
                "EXPLAIN SELECT jp.id FROM crawler.job_post jp"
                        + " LEFT JOIN crawler.pull_target pt ON jp.target_id = pt.id WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        appendEstimateFilters(sql, params, query);

        jakarta.persistence.Query nativeQuery = em.createNativeQuery(sql.toString());
        org.hibernate.query.NativeQuery<?> hibernateQuery =
                nativeQuery.unwrap(org.hibernate.query.NativeQuery.class);
        params.forEach((name, value) -> {
            if (value instanceof Collection<?> collection) {
                hibernateQuery.setParameterList(name, collection);
            } else {
                hibernateQuery.setParameter(name, value);
            }
        });

        return parseRowEstimate(hibernateQuery.getResultList());
    }

    private static final Pattern ROWS_PATTERN = Pattern.compile("rows=(\\d+)");

    private static long parseRowEstimate(List<?> planLines) {
        if (planLines == null || planLines.isEmpty()) {
            return 0L;
        }
        Object first = planLines.get(0);
        String line = first instanceof Object[] arr ? String.valueOf(arr[0]) : String.valueOf(first);
        Matcher matcher = ROWS_PATTERN.matcher(line);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : 0L;
    }

    @Override
    public JobFacets facets(JobSearchQuery query) {
        List<FacetValue> companies = facetQueryFiltered(
                "SELECT t.companyName, COUNT(j) FROM JobPostEntity j JOIN j.target t WHERE 1=1",
                " AND t.companyName IS NOT NULL GROUP BY t.companyName ORDER BY t.companyName",
                query, Dimension.COMPANY);

        List<FacetValue> employmentTypes = facetQueryFiltered(
                "SELECT j.employmentType, COUNT(j) FROM JobPostEntity j LEFT JOIN j.target t WHERE 1=1",
                " AND j.employmentType IS NOT NULL GROUP BY j.employmentType ORDER BY j.employmentType",
                query, Dimension.EMPLOYMENT_TYPE);

        List<FacetValue> careerLevels = facetQueryFiltered(
                "SELECT j.careerLevel, COUNT(j) FROM JobPostEntity j LEFT JOIN j.target t WHERE 1=1",
                " AND j.careerLevel IS NOT NULL GROUP BY j.careerLevel ORDER BY j.careerLevel",
                query, Dimension.CAREER_LEVEL);

        List<FacetValue> locations = locationFacets(query);
        List<FacetValue> languages = languageFacets(query);

        Object[] comp = compRange(query);
        Integer compMin = comp[0] == null ? null : ((Number) comp[0]).intValue();
        Integer compMax = comp[1] == null ? null : ((Number) comp[1]).intValue();

        return new JobFacets(companies, locations, languages, employmentTypes, careerLevels, compMin, compMax);
    }

    /**
     * Crawl-data generation stamp (ADR 0020): a single cheap read, native SQL because
     * JPQL has no {@code GREATEST}/{@code EXTRACT(EPOCH ...)} support through the JPA
     * API. {@code COALESCE(..., 0)} covers the empty-table case (fresh/pre-crawl
     * environment, AC-332-15): no rows means no {@code MAX}, so the scalar would
     * otherwise be {@code NULL}.
     */
    @Override
    public long facetDataVersion() {
        jakarta.persistence.Query nativeQuery = em.createNativeQuery(
                "SELECT COALESCE(EXTRACT(EPOCH FROM GREATEST(MAX(last_seen_at), MAX(enriched_at))) * 1000, 0)"
                        + " FROM crawler.job_post");
        Object result = nativeQuery.getSingleResult();
        return ((Number) result).longValue();
    }

    // ─── Public method for unit testing the exclusion logic ──────────────────

    /**
     * Composes every filter clause from {@code query} EXCEPT the one for {@code excluded},
     * appending to {@code jpql} and populating {@code params}.
     *
     * <p>Calling this with every {@link Dimension} excluded in turn guarantees that each
     * facet group's WHERE clause is identical to the search WHERE for the shared filters —
     * they cannot drift because they share the same per-clause appenders.
     */
    public void appendFiltersExcept(StringBuilder jpql, Map<String, Object> params,
            JobSearchQuery query, Dimension excluded) {
        // keyword and postedWithin are always applied — they are not facet groups
        appendKeyword(jpql, params, query);
        appendPostedWithin(jpql, params, query);

        if (excluded != Dimension.LOCATION) {
            appendLocation(jpql, params, query);
        }
        if (excluded != Dimension.COMPANY) {
            appendCompany(jpql, params, query);
        }
        if (excluded != Dimension.EMPLOYMENT_TYPE) {
            appendEmploymentType(jpql, params, query);
        }
        if (excluded != Dimension.CAREER_LEVEL) {
            appendCareerLevel(jpql, params, query);
        }
        if (excluded != Dimension.COMPENSATION) {
            appendCompensation(jpql, params, query);
        }
        if (excluded != Dimension.LANGUAGE) {
            appendLanguage(jpql, params, query);
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Runs an aggregate facet query of the form:
     * {@code <prefix> [WHERE clauses from appendFiltersExcept] <suffix>}
     * and returns the result as a list of {@link FacetValue}.
     */
    private List<FacetValue> facetQueryFiltered(String prefix, String suffix,
            JobSearchQuery query, Dimension excluded) {
        StringBuilder jpql = new StringBuilder(prefix);
        Map<String, Object> params = new HashMap<>();
        appendFiltersExcept(jpql, params, query, excluded);
        jpql.append(suffix);

        var typed = em.createQuery(jpql.toString(), Object[].class);
        params.forEach(typed::setParameter);
        return typed.getResultList().stream()
                .map(r -> new FacetValue((String) r[0], ((Number) r[1]).longValue()))
                .toList();
    }

    /**
     * Distinct countries across ALL of each post's openings (primary column plus
     * {@code job_post_location} child rows), counting a post at most once per country
     * (ADR 0017 BR-4/BR-6). JPQL has no UNION, so this merges two "distinct (post,
     * country)" queries in Java: one over the primary columns (backward-compatible with
     * posts that have no child rows yet), one over the child table. A {@code Set} collapses
     * duplicate (post, country) pairs — e.g. a post with two same-country child rows, or a
     * post whose primary country is mirrored by its own primary child row — before the
     * per-country counts are taken, so sums stay "postings per country", never "openings
     * per country". Kept as two small queries rather than one bigger one to keep the facet
     * cost bounded and each half independently indexable (idx_job_post_location_lower_country).
     */
    private List<FacetValue> locationFacets(JobSearchQuery query) {
        Map<String, java.util.Set<UUID>> postIdsByCountry = new HashMap<>();

        // Primary-column contributions (backward compatible: works even with no child rows).
        StringBuilder primaryJpql = new StringBuilder(
                "SELECT j.id, j.country FROM JobPostEntity j LEFT JOIN j.target t WHERE 1=1");
        Map<String, Object> primaryParams = new HashMap<>();
        appendFiltersExcept(primaryJpql, primaryParams, query, Dimension.LOCATION);
        primaryJpql.append(" AND j.country IS NOT NULL AND LOWER(j.country) <> 'remote'");

        var primaryTyped = em.createQuery(primaryJpql.toString(), Object[].class);
        primaryParams.forEach(primaryTyped::setParameter);
        for (Object[] row : primaryTyped.getResultList()) {
            postIdsByCountry.computeIfAbsent((String) row[1], k -> new java.util.HashSet<>())
                    .add((UUID) row[0]);
        }

        // Additional-opening (child-row) contributions.
        StringBuilder childJpql = new StringBuilder(
                "SELECT j.id, l.country FROM JobPostEntity j LEFT JOIN j.target t,"
                        + " JobPostLocationEntity l WHERE l.jobPostId = j.id");
        Map<String, Object> childParams = new HashMap<>();
        appendFiltersExcept(childJpql, childParams, query, Dimension.LOCATION);
        childJpql.append(" AND l.country IS NOT NULL AND LOWER(l.country) <> 'remote'");

        var childTyped = em.createQuery(childJpql.toString(), Object[].class);
        childParams.forEach(childTyped::setParameter);
        for (Object[] row : childTyped.getResultList()) {
            postIdsByCountry.computeIfAbsent((String) row[1], k -> new java.util.HashSet<>())
                    .add((UUID) row[0]);
        }

        List<FacetValue> locations = postIdsByCountry.entrySet().stream()
                .map(e -> new FacetValue(e.getKey(), (long) e.getValue().size()))
                .sorted(Comparator.comparing(FacetValue::value))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        // Synthetic "Remote" bucket: distinct posts with Remote anywhere in their opening set.
        java.util.Set<UUID> remotePostIds = new java.util.HashSet<>();

        StringBuilder remotePrimaryJpql = new StringBuilder(
                "SELECT j.id FROM JobPostEntity j LEFT JOIN j.target t WHERE 1=1");
        Map<String, Object> remotePrimaryParams = new HashMap<>();
        appendFiltersExcept(remotePrimaryJpql, remotePrimaryParams, query, Dimension.LOCATION);
        remotePrimaryJpql.append(" AND (LOWER(j.city) = 'remote' OR LOWER(j.country) = 'remote')");
        var remotePrimaryTyped = em.createQuery(remotePrimaryJpql.toString(), UUID.class);
        remotePrimaryParams.forEach(remotePrimaryTyped::setParameter);
        remotePostIds.addAll(remotePrimaryTyped.getResultList());

        StringBuilder remoteChildJpql = new StringBuilder(
                "SELECT j.id FROM JobPostEntity j LEFT JOIN j.target t,"
                        + " JobPostLocationEntity l WHERE l.jobPostId = j.id");
        Map<String, Object> remoteChildParams = new HashMap<>();
        appendFiltersExcept(remoteChildJpql, remoteChildParams, query, Dimension.LOCATION);
        remoteChildJpql.append(" AND (LOWER(l.city) = 'remote' OR LOWER(l.country) = 'remote')");
        var remoteChildTyped = em.createQuery(remoteChildJpql.toString(), UUID.class);
        remoteChildParams.forEach(remoteChildTyped::setParameter);
        remotePostIds.addAll(remoteChildTyped.getResultList());

        if (!remotePostIds.isEmpty()) {
            locations.add(new FacetValue(REMOTE, remotePostIds.size()));
        }

        return locations;
    }

    // languages is a text[] column — unnest it (native SQL; JPQL has no array unnest).
    @SuppressWarnings("unchecked")
    private List<FacetValue> languageFacets(JobSearchQuery query) {
        // Build the parameterised AND clauses from the non-language filters, then embed them
        // in native SQL.  JPQL aliases are replaced with real column/table names.  The column
        // and table names match the entity mappings exactly — this is the only native-SQL path,
        // kept narrow on purpose.
        StringBuilder andClauses = new StringBuilder();
        Map<String, Object> andParams = new HashMap<>();
        appendFiltersExcept(andClauses, andParams, query, Dimension.LANGUAGE);

        // Convert JPQL aliases to native SQL equivalents for the crawler.job_post table:
        //   j.  → jp.    (job_post columns)
        //   t.  → pt.    (pull_target columns)
        //   l.  → l.     (job_post_location child-row columns, Story #1 / ADR 0017 — the
        //                 EXISTS subquery's FROM/alias itself is translated separately below,
        //                 since JPQL's "JobPostLocationEntity l" has no 1:1 native column rename)
        String nativeWhere = andClauses.toString()
                .replace("j.title", "jp.title")
                .replace("j.description", "jp.description")
                .replace("j.city", "jp.city")
                .replace("j.country", "jp.country")
                .replace("t.companyName", "pt.company_name")
                .replace("j.employmentType", "jp.employment_type")
                .replace("j.careerLevel", "jp.career_level")
                .replace("j.compensationMin", "jp.compensation_min")
                .replace("j.compensationMax", "jp.compensation_max")
                .replace("j.firstSeenAt", "jp.first_seen_at")
                .replace("j.languages", "jp.languages")
                // JPQL array_overlaps becomes native array_overlaps; already native-compatible
                .replace("array_overlaps(jp.languages,", "array_overlaps(jp.languages,")
                // FTS keyword clause: j.searchVector → jp.search_vector must happen BEFORE
                // the sql() envelope replacement below, since the envelope references
                // jp.search_vector by its post-replacement name.
                .replace("j.searchVector", "jp.search_vector")
                .replace("cast(sql('? @@ plainto_tsquery(''english'', ?)', jp.search_vector, :keyword) as boolean) = true",
                         "jp.search_vector @@ plainto_tsquery('english', :keyword)")
                // Story #1 (ADR 0017): appendLocation()'s EXISTS-over-child-table fragment.
                // None of the j./t. renames above touch "j.id" (the correlation column), so
                // it is translated explicitly here alongside the child entity's FROM clause.
                .replace("EXISTS (SELECT 1 FROM JobPostLocationEntity l WHERE l.jobPostId = j.id",
                         "EXISTS (SELECT 1 FROM crawler.job_post_location l WHERE l.job_post_id = jp.id");

        String baseSql = "SELECT lang, COUNT(*) FROM crawler.job_post jp"
                + " LEFT JOIN crawler.pull_target pt ON jp.target_id = pt.id,"
                + " unnest(jp.languages) AS lang"
                + " WHERE 1=1"
                + nativeWhere
                + " GROUP BY lang ORDER BY lang";

        jakarta.persistence.Query nativeQuery = em.createNativeQuery(baseSql);
        andParams.forEach(nativeQuery::setParameter);

        List<Object[]> rows = nativeQuery.getResultList();
        return rows.stream()
                .map(r -> new FacetValue((String) r[0], ((Number) r[1]).longValue()))
                .toList();
    }

    private Object[] compRange(JobSearchQuery query) {
        StringBuilder jpql = new StringBuilder(
                "SELECT MIN(j.compensationMin), MAX(j.compensationMax)"
                        + " FROM JobPostEntity j LEFT JOIN j.target t WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        appendFiltersExcept(jpql, params, query, Dimension.COMPENSATION);

        var typed = em.createQuery(jpql.toString(), Object[].class);
        params.forEach(typed::setParameter);
        return (Object[]) typed.getSingleResult();
    }

    // ─── Per-dimension filter appenders ──
    // (appendKeyword now uses FTS — see Story #52 / ADR 0005; the rest are unchanged)

    /**
     * Public for unit testing (see {@code AppendKeywordTest}) — same rationale as
     * {@link #appendFiltersExcept}: pure JPQL-string + param-map construction, no DB.
     */
    public void appendKeyword(StringBuilder jpql, Map<String, Object> params, JobSearchQuery query) {
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            // sql(...) has an unresolved (Object) HQL return type, so Hibernate 7's
            // type-checker rejects "sql(...) = true" directly (Object vs Boolean).
            // cast(... as boolean) gives the comparison a concrete boolean type while
            // keeping the same sql()/@@/plainto_tsquery escape and :keyword binding the
            // ADR specifies. languageFacets() strips this same envelope for the native path.
            jpql.append(" AND cast(sql('? @@ plainto_tsquery(''english'', ?)', j.searchVector, :keyword) as boolean) = true");
            params.put("keyword", query.getKeyword());
        }
    }

    /**
     * A post matches a location value when EITHER its primary columns match OR any of its
     * {@code job_post_location} child rows match (ADR 0017 / BR-7) — OR-ed together per
     * value, so a post with only primary-column data (no child rows yet) keeps matching
     * exactly as before (regression-safe), while a post with additional openings also
     * matches on those. The EXISTS subquery is correlated on {@code j.id} and deliberately
     * NOT a JOIN, so a post with several matching openings is never duplicated in the
     * result (AC-1-FILTER-2).
     */
    private void appendLocation(StringBuilder jpql, Map<String, Object> params, JobSearchQuery query) {
        if (query.getLocations() == null || query.getLocations().isEmpty()) {
            return;
        }
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
                jpql.append("(LOWER(j.city) = 'remote' OR LOWER(j.country) = 'remote'")
                        .append(" OR EXISTS (SELECT 1 FROM JobPostLocationEntity l WHERE l.jobPostId = j.id")
                        .append(" AND (LOWER(l.city) = 'remote' OR LOWER(l.country) = 'remote')))");
                continue;
            }
            String[] parts = loc.split(",", 2);
            if (parts.length == 2) {
                String cityKey = "loc_city_" + i;
                String countryKey = "loc_country_" + i;
                jpql.append("((LOWER(j.city) = :").append(cityKey)
                        .append(" AND LOWER(j.country) = :").append(countryKey).append(")")
                        .append(" OR EXISTS (SELECT 1 FROM JobPostLocationEntity l WHERE l.jobPostId = j.id")
                        .append(" AND LOWER(l.city) = :").append(cityKey)
                        .append(" AND LOWER(l.country) = :").append(countryKey).append("))");
                params.put(cityKey, parts[0].trim().toLowerCase());
                params.put(countryKey, parts[1].trim().toLowerCase());
            } else {
                String key = "loc_" + i;
                jpql.append("((LOWER(j.city) = :").append(key)
                        .append(" OR LOWER(j.country) = :").append(key).append(")")
                        .append(" OR EXISTS (SELECT 1 FROM JobPostLocationEntity l WHERE l.jobPostId = j.id")
                        .append(" AND (LOWER(l.city) = :").append(key)
                        .append(" OR LOWER(l.country) = :").append(key).append(")))");
                params.put(key, loc.trim().toLowerCase());
            }
            i++;
        }
        if (first) {
            jpql.append("1=1");
        }
        jpql.append(")");
    }

    private void appendCompany(StringBuilder jpql, Map<String, Object> params, JobSearchQuery query) {
        if (query.getCompanies() != null && !query.getCompanies().isEmpty()) {
            jpql.append(" AND LOWER(t.companyName) IN :companies");
            params.put("companies", query.getCompanies().stream().map(String::toLowerCase).toList());
        }
    }

    private void appendEmploymentType(StringBuilder jpql, Map<String, Object> params, JobSearchQuery query) {
        if (query.getEmploymentTypes() != null && !query.getEmploymentTypes().isEmpty()) {
            jpql.append(" AND j.employmentType IN :employmentTypes");
            params.put("employmentTypes",
                    query.getEmploymentTypes().stream().map(EmploymentType::value).toList());
        }
    }

    private void appendCareerLevel(StringBuilder jpql, Map<String, Object> params, JobSearchQuery query) {
        if (query.getCareerLevels() != null && !query.getCareerLevels().isEmpty()) {
            jpql.append(" AND j.careerLevel IN :careerLevels");
            params.put("careerLevels",
                    query.getCareerLevels().stream().map(CareerLevel::value).toList());
        }
    }

    private void appendCompensation(StringBuilder jpql, Map<String, Object> params, JobSearchQuery query) {
        if (query.getCompensationMin() != null) {
            jpql.append(" AND j.compensationMin >= :compMin");
            params.put("compMin", query.getCompensationMin());
        }
        if (query.getCompensationMax() != null) {
            jpql.append(" AND (j.compensationMax IS NULL OR j.compensationMax <= :compMax)");
            params.put("compMax", query.getCompensationMax());
        }
    }

    private void appendPostedWithin(StringBuilder jpql, Map<String, Object> params, JobSearchQuery query) {
        PostedWithin posted = query.getPostedWithin();
        if (posted != null) {
            jpql.append(" AND j.firstSeenAt >= :postedSince");
            params.put("postedSince", OffsetDateTime.now().minus(posted.window()));
        }
    }

    /**
     * Story #407 fix: {@code array_overlaps(j.languages, :languages)} (Hibernate's built-in
     * {@code &&} HQL function) resolves {@code :languages}' bound JDBC array type from
     * {@code j.languages}' own mapped Java type - {@code List<String>} defaults to
     * {@code varchar[]} (Hibernate's standard mapping for {@code String}), regardless of the
     * column's actual {@code columnDefinition}. Against prod's real {@code text[]} column
     * (db/init/010-crawler.sql), Postgres has no implicit cast between {@code text[]} and
     * {@code varchar[]} for operator resolution (unlike the scalar-to-scalar case), so it
     * raises "operator does not exist: text[] && character varying[]" -> HTTP 500. Escaping
     * to raw SQL (same {@code sql()} pattern already used by {@link #appendKeyword} for FTS)
     * and casting the bound parameter explicitly to {@code text[]} sidesteps Hibernate's
     * array-type inference entirely, matching the real column type regardless of what
     * Hibernate infers for the parameter.
     */
    private void appendLanguage(StringBuilder jpql, Map<String, Object> params, JobSearchQuery query) {
        if (query.getLanguages() != null && !query.getLanguages().isEmpty()) {
            jpql.append(" AND cast(sql('? && ?::text[]', j.languages, :languages) as boolean) = true");
            params.put("languages", query.getLanguages().toArray(new String[0]));
        }
    }

    // ─── Composite filter (all dimensions — used by search/count) ────────────

    private void appendFilters(StringBuilder jpql, Map<String, Object> params, JobSearchQuery query) {
        appendKeyword(jpql, params, query);
        appendLocation(jpql, params, query);
        appendCompany(jpql, params, query);
        appendEmploymentType(jpql, params, query);
        appendCareerLevel(jpql, params, query);
        appendCompensation(jpql, params, query);
        appendPostedWithin(jpql, params, query);
        appendLanguage(jpql, params, query);
    }

    // ─── Native-SQL filter (EXPLAIN step only, see estimateCount) ────────────
    // Mirrors appendFilters()'s seven dimensions one-to-one, in native SQL with real
    // table/column names, because EXPLAIN has no JPQL/HQL entry point through JPA.
    // Kept immediately below appendFilters for easy side-by-side review; a change to
    // one filter dimension's semantics should always be mirrored in the other.

    private void appendEstimateFilters(StringBuilder sql, Map<String, Object> params, JobSearchQuery query) {
        appendEstimateKeyword(sql, params, query);
        appendEstimateLocation(sql, params, query);
        appendEstimateCompany(sql, params, query);
        appendEstimateEmploymentType(sql, params, query);
        appendEstimateCareerLevel(sql, params, query);
        appendEstimateCompensation(sql, params, query);
        appendEstimatePostedWithin(sql, params, query);
        appendEstimateLanguage(sql, params, query);
    }

    private void appendEstimateKeyword(StringBuilder sql, Map<String, Object> params, JobSearchQuery query) {
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            sql.append(" AND jp.search_vector @@ plainto_tsquery('english', :keyword)");
            params.put("keyword", query.getKeyword());
        }
    }

    private void appendEstimateLocation(StringBuilder sql, Map<String, Object> params, JobSearchQuery query) {
        if (query.getLocations() == null || query.getLocations().isEmpty()) {
            return;
        }
        sql.append(" AND (");
        boolean first = true;
        int i = 0;
        for (String loc : query.getLocations()) {
            if (loc == null || loc.isBlank()) {
                continue;
            }
            if (!first) {
                sql.append(" OR ");
            }
            first = false;
            if (REMOTE.equalsIgnoreCase(loc.trim())) {
                sql.append("(LOWER(jp.city) = 'remote' OR LOWER(jp.country) = 'remote'")
                        .append(" OR EXISTS (SELECT 1 FROM crawler.job_post_location l WHERE l.job_post_id = jp.id")
                        .append(" AND (LOWER(l.city) = 'remote' OR LOWER(l.country) = 'remote')))");
                continue;
            }
            String[] parts = loc.split(",", 2);
            if (parts.length == 2) {
                String cityKey = "loc_city_" + i;
                String countryKey = "loc_country_" + i;
                sql.append("((LOWER(jp.city) = :").append(cityKey)
                        .append(" AND LOWER(jp.country) = :").append(countryKey).append(")")
                        .append(" OR EXISTS (SELECT 1 FROM crawler.job_post_location l WHERE l.job_post_id = jp.id")
                        .append(" AND LOWER(l.city) = :").append(cityKey)
                        .append(" AND LOWER(l.country) = :").append(countryKey).append("))");
                params.put(cityKey, parts[0].trim().toLowerCase());
                params.put(countryKey, parts[1].trim().toLowerCase());
            } else {
                String key = "loc_" + i;
                sql.append("((LOWER(jp.city) = :").append(key)
                        .append(" OR LOWER(jp.country) = :").append(key).append(")")
                        .append(" OR EXISTS (SELECT 1 FROM crawler.job_post_location l WHERE l.job_post_id = jp.id")
                        .append(" AND (LOWER(l.city) = :").append(key)
                        .append(" OR LOWER(l.country) = :").append(key).append(")))");
                params.put(key, loc.trim().toLowerCase());
            }
            i++;
        }
        if (first) {
            sql.append("1=1");
        }
        sql.append(")");
    }

    private void appendEstimateCompany(StringBuilder sql, Map<String, Object> params, JobSearchQuery query) {
        if (query.getCompanies() != null && !query.getCompanies().isEmpty()) {
            sql.append(" AND LOWER(pt.company_name) IN (:companies)");
            params.put("companies", query.getCompanies().stream().map(String::toLowerCase).toList());
        }
    }

    private void appendEstimateEmploymentType(StringBuilder sql, Map<String, Object> params, JobSearchQuery query) {
        if (query.getEmploymentTypes() != null && !query.getEmploymentTypes().isEmpty()) {
            sql.append(" AND jp.employment_type IN (:employmentTypes)");
            params.put("employmentTypes",
                    query.getEmploymentTypes().stream().map(EmploymentType::value).toList());
        }
    }

    private void appendEstimateCareerLevel(StringBuilder sql, Map<String, Object> params, JobSearchQuery query) {
        if (query.getCareerLevels() != null && !query.getCareerLevels().isEmpty()) {
            sql.append(" AND jp.career_level IN (:careerLevels)");
            params.put("careerLevels",
                    query.getCareerLevels().stream().map(CareerLevel::value).toList());
        }
    }

    private void appendEstimateCompensation(StringBuilder sql, Map<String, Object> params, JobSearchQuery query) {
        if (query.getCompensationMin() != null) {
            sql.append(" AND jp.compensation_min >= :compMin");
            params.put("compMin", query.getCompensationMin());
        }
        if (query.getCompensationMax() != null) {
            sql.append(" AND (jp.compensation_max IS NULL OR jp.compensation_max <= :compMax)");
            params.put("compMax", query.getCompensationMax());
        }
    }

    private void appendEstimatePostedWithin(StringBuilder sql, Map<String, Object> params, JobSearchQuery query) {
        PostedWithin posted = query.getPostedWithin();
        if (posted != null) {
            sql.append(" AND jp.first_seen_at >= :postedSince");
            params.put("postedSince", OffsetDateTime.now().minus(posted.window()));
        }
    }

    // A single-scalar "= ANY(array_column)" per language, OR-ed together, rather than
    // binding a native array parameter for "&&": avoids driver/Hibernate-version
    // dependent array-type coercion on a raw native query for a best-effort estimate.
    private void appendEstimateLanguage(StringBuilder sql, Map<String, Object> params, JobSearchQuery query) {
        if (query.getLanguages() == null || query.getLanguages().isEmpty()) {
            return;
        }
        sql.append(" AND (");
        List<String> languages = query.getLanguages();
        for (int i = 0; i < languages.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            String key = "lang_" + i;
            sql.append(":").append(key).append(" = ANY(jp.languages)");
            params.put(key, languages.get(i));
        }
        sql.append(")");
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
