package com.davidcreate.jobhub.crawler.domain.port.out;

import com.davidcreate.jobhub.crawler.domain.model.JobEnrichment;
import com.davidcreate.jobhub.crawler.domain.model.JobPost;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobPostRepository {

    Optional<JobPost> findByContentHash(String contentHash);

    /** Look up an existing posting by its (unique) URL — used to avoid duplicate-URL inserts. */
    Optional<JobPost> findByUrl(String url);

    /**
     * Persists the post plus its full opening set (primary child row + any additional
     * openings) in one transaction (ADR 0017): there is never a moment where only the
     * parent {@code job_post} row exists without its child {@code job_post_location} rows.
     */
    void save(JobPost jobPost);

    /**
     * Same one-transaction guarantee as {@link #save(JobPost)}, applied to every post in
     * the batch.
     */
    void saveAll(List<JobPost> jobPosts);

    // ─── Enrichment ───────────────────────────────────────────────────────────

    /** Job posts still awaiting LLM enrichment (enrichment_status = 'pending'). */
    List<JobPost> findPendingEnrichment(int limit);

    /**
     * Apply the model's extracted fields and mark the row enriched. Fields are
     * coalesced (only non-null/non-empty values overwrite); compensation is only
     * filled when the row had none. content_hash is never touched.
     */
    void applyEnrichment(UUID id, JobEnrichment enrichment);

    /** Record a failed attempt; flips to 'failed' once maxAttempts is reached. */
    void markEnrichmentFailed(UUID id, int maxAttempts);

    // ─── Maintenance ──────────────────────────────────────────────────────────

    /**
     * Rewrite up to {@code limit} job_post rows whose description still holds HTML/markup
     * into plain text. Idempotent: each rewritten row drops out of the selection, so
     * calling repeatedly until it returns 0 cleans the whole table. Returns rows touched.
     */
    int cleanHtmlDescriptionBatch(int limit);

    /**
     * Re-normalize up to {@code limit} job_post rows that have a non-null languages array.
     * Applies {@link com.davidcreate.jobhub.crawler.adapter.out.client.support.EnrichmentParser#normalizeLanguages}
     * in-process — no LLM call is made. Rows whose normalized result is unchanged are
     * still counted (idempotent). Returns the number of rows examined (not rows changed),
     * so calling repeatedly until it returns 0 processes the whole table.
     */
    int normalizeLanguagesBatch(int limit);

    /**
     * Re-normalize up to {@code limit} job_post rows with {@code id > afterId} (ascending-id
     * cursor, story #408 / ADR 0021 section 6: a page-0 selection would loop forever since a
     * normalized row keeps a non-null city/country). Applies {@link
     * com.davidcreate.jobhub.crawler.adapter.out.client.support.LocationNormalizer#normalizePair}
     * in-process, rewrites the parent {@code city}/{@code country} columns to the primary
     * opening, and rewrites the row's {@code job_post_location} child set to match (ADR 0017's
     * primary-mirror invariant). Never touches {@code content_hash}. Pass {@code null} for the
     * first page; the caller advances the cursor to {@link LocationBatchResult#lastId()} until
     * an empty result is returned.
     */
    LocationBatchResult normalizeLocationsBatch(UUID afterId, int limit);
}
