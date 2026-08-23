package com.davidcreate.jobhub.crawler.adapter.out.persistence;

import com.davidcreate.jobhub.crawler.adapter.out.client.support.EnrichmentParser;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.HtmlToText;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.LocationNormalizer;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.NormalizedLocation;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.JobPostEntity;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.JobPostLocationEntity;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.mapper.JobPostMapper;
import com.davidcreate.jobhub.crawler.domain.model.JobEnrichment;
import com.davidcreate.jobhub.crawler.domain.model.JobPost;
import com.davidcreate.jobhub.crawler.domain.port.out.JobPostRepository;
import com.davidcreate.jobhub.crawler.domain.port.out.LocationBatchResult;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class JobPostPanacheRepository implements JobPostRepository, PanacheRepositoryBase<JobPostEntity, UUID> {

    private static final Logger LOG = Logger.getLogger(JobPostPanacheRepository.class);

    private final JobPostMapper mapper;
    private final EntityManager entityManager;

    @Override
    public Optional<JobPost> findByContentHash(String contentHash) {
        return find("contentHash", contentHash)
                .firstResultOptional()
                .map(mapper::toDomain);
    }

    @Override
    public Optional<JobPost> findByUrl(String url) {
        return find("url", url)
                .firstResultOptional()
                .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void save(JobPost domain) {
        find("contentHash", domain.getContentHash())
                .firstResultOptional()
                .ifPresentOrElse(
                        entity -> {
                            mapper.updateEntity(entity, domain);
                            persistAndFlush(entity);
                            syncLocations(entity.id, domain);
                            LOG.debugf("UPDATE crawler.job_post id=%s contentHash=%s", entity.id, entity.contentHash);
                        },
                        () -> {
                            JobPostEntity entity = mapper.toEntity(domain);
                            persistAndFlush(entity);
                            syncLocations(entity.id, domain);
                            LOG.debugf("INSERT crawler.job_post id=%s contentHash=%s", entity.id, entity.contentHash);
                        });
    }

    @Override
    @Transactional
    public void saveAll(List<JobPost> jobs) {
        int inserted = 0;
        int updated = 0;
        for (JobPost domain : jobs) {
            Optional<JobPostEntity> existing = find("contentHash", domain.getContentHash())
                    .firstResultOptional();
            if (existing.isPresent()) {
                JobPostEntity entity = existing.get();
                mapper.updateEntity(entity, domain);
                persist(entity);
                flush();
                syncLocations(entity.id, domain);
                updated++;
            } else {
                JobPostEntity entity = mapper.toEntity(domain);
                persist(entity);
                flush();
                syncLocations(entity.id, domain);
                inserted++;
            }
        }
        LOG.debugf("UPSERT crawler.job_post inserted=%d updated=%d", inserted, updated);
    }

    /**
     * Replaces this post's {@code crawler.job_post_location} rows with the full opening
     * set derived from {@code domain} (primary mirrors {@code job_post.city}/{@code country}
     * plus any additional openings, deduped by (country, city)). Delete-then-reinsert keeps
     * the child table in sync on every write, in the same transaction as the parent row
     * (ADR 0017): there is never a moment where only the parent exists without its children.
     */
    private void syncLocations(UUID jobPostId, JobPost domain) {
        JobPostLocationEntity.delete("jobPostId", jobPostId);
        entityManager.flush();
        for (JobPostLocationEntity locationEntity : mapper.toLocationEntities(jobPostId, domain)) {
            locationEntity.createdAt = OffsetDateTime.now();
            entityManager.persist(locationEntity);
        }
        entityManager.flush();
    }

    // ─── Enrichment ───────────────────────────────────────────────────────────

    // Fetches `pending` rows without claiming them: they stay `pending` through the
    // slow, out-of-transaction model call and only flip to done/failed afterwards.
    // Safe for a SINGLE crawler instance (the only deployment today; @Scheduled SKIP
    // also blocks same-instance overlap). If ever scaled to multiple instances, two
    // workers would fetch and enrich the same rows. To make it multi-instance-safe,
    // claim a disjoint batch atomically — e.g. UPDATE ... SET enrichment_status =
    // 'processing' WHERE id IN (SELECT id ... WHERE enrichment_status = 'pending'
    // LIMIT :n FOR UPDATE SKIP LOCKED) RETURNING * — plus a reaper that resets stale
    // `processing` rows back to `pending` (covers an instance crashing mid-call).
    @Override
    @Transactional
    public List<JobPost> findPendingEnrichment(int limit) {
        return find("enrichmentStatus", "pending")
                .page(0, limit)
                .list()
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void applyEnrichment(UUID id, JobEnrichment e) {
        JobPostEntity job = findById(id);
        if (job == null) {
            return;
        }
        if (e.employmentType() != null) {
            job.employmentType = e.employmentType();
        }
        if (e.careerLevel() != null) {
            job.careerLevel = e.careerLevel();
        }
        if (e.languages() != null && !e.languages().isEmpty()) {
            job.languages = e.languages();
        }
        if (e.requirements() != null && !e.requirements().isEmpty()) {
            job.requirements = e.requirements();
        }
        if (e.city() != null) {
            job.city = e.city();
        }
        if (e.country() != null) {
            job.country = e.country();
        }
        // Deterministic salary (regex) wins; only fill when the row had none.
        if (job.compensationMin == null && e.compensationMinEur() != null) {
            job.compensationMin = e.compensationMinEur();
            job.compensationMax = e.compensationMaxEur();
        }
        job.enrichmentStatus = "done";
        job.enrichedAt = OffsetDateTime.now();
        persist(job);
        LOG.debugf("UPDATE crawler.job_post id=%s enrichmentStatus=done", job.id);
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void markEnrichmentFailed(UUID id, int maxAttempts) {
        JobPostEntity job = findById(id);
        if (job == null) {
            return;
        }
        job.enrichmentAttempts = (short) (job.enrichmentAttempts + 1);
        if (job.enrichmentAttempts >= maxAttempts) {
            job.enrichmentStatus = "failed";
        }
        persist(job);
        LOG.debugf("UPDATE crawler.job_post id=%s enrichmentAttempts=%d enrichmentStatus=%s",
                job.id, job.enrichmentAttempts, job.enrichmentStatus);
    }

    // ─── Maintenance ──────────────────────────────────────────────────────────

    // Rows whose description still carries HTML tags ("</") or unresolved entities.
    private static final String HTML_FILTER =
            "description like '%</%' or description like '%&lt;%' or description like '%&gt;%' "
            + "or description like '%&#%' or description like '%&amp;%' or description like '%&nbsp;%'";

    @Override
    @Transactional
    public int cleanHtmlDescriptionBatch(int limit) {
        List<JobPostEntity> rows = find(HTML_FILTER).page(0, limit).list();
        for (JobPostEntity row : rows) {
            // Overwriting with cleaned text removes the markers, so the row drops out of
            // HTML_FILTER and is never re-selected — the loop in the caller terminates.
            row.description = HtmlToText.clean(row.description);
        }
        if (!rows.isEmpty()) {
            LOG.infof("Maintenance: cleaned HTML from %d job_post descriptions", rows.size());
        }
        return rows.size();
    }

    @Override
    @Transactional
    public int normalizeLanguagesBatch(int limit) {
        List<JobPostEntity> rows = find("languages is not null").page(0, limit).list();
        for (JobPostEntity row : rows) {
            List<String> normalized = EnrichmentParser.normalizeLanguages(row.languages);
            row.languages = normalized.isEmpty() ? null : normalized;
        }
        if (!rows.isEmpty()) {
            LOG.infof("Maintenance: re-normalized languages for %d job_post rows", rows.size());
        }
        return rows.size();
    }

    @Override
    @Transactional
    public LocationBatchResult normalizeLocationsBatch(UUID afterId, int limit) {
        List<JobPostEntity> rows = afterId == null
                ? find("ORDER BY id").page(0, limit).list()
                : find("id > ?1 ORDER BY id", afterId).page(0, limit).list();
        if (rows.isEmpty()) {
            return LocationBatchResult.EMPTY;
        }
        for (JobPostEntity row : rows) {
            List<NormalizedLocation> openings = LocationNormalizer.normalizePair(row.city, row.country);
            NormalizedLocation primary = openings.isEmpty() ? null : openings.get(0);
            row.city = primary == null ? null : primary.city();
            row.country = primary == null ? null : primary.country();

            JobPostLocationEntity.delete("jobPostId", row.id);
            entityManager.flush();
            short position = 0;
            for (NormalizedLocation opening : openings) {
                JobPostLocationEntity child = new JobPostLocationEntity();
                child.jobPostId = row.id;
                child.city = opening.city();
                child.country = opening.country();
                child.isPrimary = position == 0;
                child.position = position;
                child.createdAt = OffsetDateTime.now();
                entityManager.persist(child);
                position++;
            }
        }
        entityManager.flush();
        UUID lastId = rows.get(rows.size() - 1).id;
        LOG.infof("Maintenance: re-normalized locations for %d job_post rows", rows.size());
        return new LocationBatchResult(lastId, rows.size());
    }
}