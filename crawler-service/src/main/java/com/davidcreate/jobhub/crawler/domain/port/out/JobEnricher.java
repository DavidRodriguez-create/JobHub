package com.davidcreate.jobhub.crawler.domain.port.out;

import com.davidcreate.jobhub.crawler.domain.model.JobEnrichment;

/**
 * Extracts structured fields from a posting's free text. Implementations only
 * ever receive target-derived data (title, description, and the best-effort
 * parsed city/country used purely as a location hint) — never URLs or other
 * technical fields. Throws on any call/parse failure so the caller can retry.
 */
public interface JobEnricher {

    JobEnrichment enrich(String title, String description, String city, String country);
}
