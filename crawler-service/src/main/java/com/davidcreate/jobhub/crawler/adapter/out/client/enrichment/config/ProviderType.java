package com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.config;

/**
 * Selects the HTTP client and request/response format an
 * {@link com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider.EnrichmentProvider}
 * uses for a configured provider entry.
 */
public enum ProviderType {
    GEMINI, OPENAI, OLLAMA
}
