package com.davidcreate.jobhub.crawler.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.JobPostEntity;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.JobPostLocationEntity;
import com.davidcreate.jobhub.crawler.domain.model.JobPost;
import com.davidcreate.jobhub.crawler.domain.model.JobPostLocation;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class JobPostMapper {

    public JobPost toDomain(JobPostEntity entity) {
        return toDomain(entity, List.of());
    }

    /**
     * @param childRows this post's {@code crawler.job_post_location} rows, in arbitrary
     *                  order. The primary row is always placed first in the resulting
     *                  domain object's {@link JobPost#locations()}: the mapper guarantees
     *                  primary-first ordering, not incidental JPA/DB ordering (QAE-CRAWL-STORE-1B).
     */
    public JobPost toDomain(JobPostEntity entity, List<JobPostLocationEntity> childRows) {
        return JobPost.builder()
                .id(entity.id)
                .targetId(entity.targetId)
                .title(entity.title)
                .url(entity.url)
                .description(entity.description)
                .contentHash(entity.contentHash)
                .city(entity.city)
                .country(entity.country)
                .additionalLocations(toAdditionalLocations(childRows))
                .compensationMin(entity.compensationMin)
                .compensationMax(entity.compensationMax)
                .employmentType(entity.employmentType)
                .languages(entity.languages)
                .requirements(entity.requirements)
                .firstSeenAt(entity.firstSeenAt)
                .lastSeenAt(entity.lastSeenAt)
                .build();
    }

    /**
     * All non-primary child rows, ordered by {@code position}. The primary row (if present
     * among {@code childRows}) is intentionally excluded here: {@link JobPost#locations()}
     * derives the primary entry from {@code city}/{@code country}, so including it again
     * from the child rows would duplicate it.
     */
    private List<JobPostLocation> toAdditionalLocations(List<JobPostLocationEntity> childRows) {
        if (childRows == null || childRows.isEmpty()) {
            return List.of();
        }
        return childRows.stream()
                .filter(row -> !row.isPrimary)
                .sorted(Comparator.comparingInt(row -> row.position))
                .map(row -> JobPostLocation.builder()
                        .country(row.country)
                        .city(row.city)
                        .primary(false)
                        .build())
                .toList();
    }

    /**
     * Builds one {@code crawler.job_post_location} child row per DISTINCT opening in
     * {@code domain}, primary first (position 0), then additional openings in order
     * (position 1..N). Duplicate {@code (country, city)} pairs within the same post are
     * deduped here (application-level idempotency backing the DB's
     * {@code uq_job_post_location_post_country_city} constraint, QAE-CRAWL-STORE-3B / BR-3);
     * the first occurrence wins, so a duplicate of the primary opening is dropped, not the
     * primary itself. Used to persist the full opening set alongside the parent
     * {@code job_post} row, in the same transaction (ADR 0017).
     */
    public List<JobPostLocationEntity> toLocationEntities(UUID jobPostId, JobPost domain) {
        List<JobPostLocationEntity> entities = new ArrayList<>();
        List<String> seenPairs = new ArrayList<>();
        int position = 0;
        for (JobPostLocation opening : domain.locations()) {
            String pairKey = locationKey(opening.getCountry(), opening.getCity());
            if (seenPairs.contains(pairKey)) {
                continue;
            }
            seenPairs.add(pairKey);

            JobPostLocationEntity entity = new JobPostLocationEntity();
            entity.jobPostId = jobPostId;
            entity.country = opening.getCountry();
            entity.city = opening.getCity();
            entity.isPrimary = opening.isPrimary();
            entity.position = (short) position;
            entities.add(entity);
            position++;
        }
        return Collections.unmodifiableList(entities);
    }

    private static String locationKey(String country, String city) {
        return (country == null ? "" : country.trim().toLowerCase())
                + "|"
                + (city == null ? "" : city.trim().toLowerCase());
    }

    public JobPostEntity toEntity(JobPost domain) {
        JobPostEntity entity = new JobPostEntity();
        entity.targetId = domain.getTargetId();
        entity.title = domain.getTitle();
        entity.url = domain.getUrl();
        entity.description = domain.getDescription();
        entity.contentHash = domain.getContentHash();
        entity.city = domain.getCity();
        entity.country = domain.getCountry();
        entity.compensationMin = domain.getCompensationMin();
        entity.compensationMax = domain.getCompensationMax();
        entity.employmentType = domain.getEmploymentType();
        entity.languages = domain.getLanguages();
        entity.requirements = domain.getRequirements();
        entity.firstSeenAt = domain.getFirstSeenAt();
        entity.lastSeenAt = domain.getLastSeenAt();
        return entity;
    }

    /**
     * Re-crawl update path. Mirrors the split primary {@code city}/{@code country} onto the
     * parent row too (ADR 0017, story #319 update-path ruling), so a re-crawled Lever posting's
     * parent columns stay in sync with the split primary child row {@code syncLocations}
     * writes in the same save; without this, the parent would keep a stale/unsplit value while
     * the child table already carries the split one. Safe/idempotent: {@code save}/{@code
     * saveAll} only reach here on a {@code contentHash} match, and {@code contentHash} is fed by
     * the raw location string, so the split is deterministic for a given hash; this method never
     * recomputes {@code content_hash} itself.
     */
    public void updateEntity(JobPostEntity entity, JobPost domain) {
        entity.title = domain.getTitle();
        entity.url = domain.getUrl();
        entity.description = domain.getDescription();
        entity.city = domain.getCity();
        entity.country = domain.getCountry();
        entity.lastSeenAt = domain.getLastSeenAt();
    }
}
