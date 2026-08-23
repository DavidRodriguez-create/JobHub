package com.davidcreate.jobhub.crawler.domain.model;

import java.util.List;

/**
 * Structured fields extracted from a posting's text by the LLM enrichment pass.
 * Every field is optional: a {@code null} (or empty list) means "the model could
 * not determine this", and the enrichment pass leaves the existing column value
 * untouched. Compensation is already converted to an EUR estimate and is only
 * populated when a figure was explicitly stated in the text (never guessed).
 */
public record JobEnrichment(
        String employmentType,
        String careerLevel,
        List<String> languages,
        List<String> requirements,
        String city,
        String country,
        Integer compensationMinEur,
        Integer compensationMaxEur) {
}
