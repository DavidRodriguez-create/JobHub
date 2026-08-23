package com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider;

import com.davidcreate.jobhub.crawler.domain.model.JobEnrichment;

/**
 * One configured LLM provider entry in the enrichment chain. Implementations
 * are adapter-layer details — NOT a domain port — and are assembled by
 * {@link EnrichmentProviderFactory} and iterated by
 * {@link com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.ProviderChainJobEnricher}
 * (job-posting enrichment). Per-model cooldown state is owned by each provider
 * instance.
 */
public interface EnrichmentProvider {

    /** Human-readable name for logging (the configured {@code name}). */
    String name();

    /**
     * Try to enrich using this provider. Returns the enrichment on success.
     * Throws on any failure (HTTP error, timeout, unparseable response, all
     * models exhausted within this provider). The chain catches and moves on.
     */
    JobEnrichment enrich(String title, String description, String city, String country);
}
