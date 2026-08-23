package com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.config;

import io.smallrye.config.ConfigMapping;

import java.util.List;

/**
 * Indexed LLM provider chain for crawler enrichment. Providers are tried in
 * declaration order (index 0 first) by
 * {@link com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.ProviderChainJobEnricher}.
 */
@ConfigMapping(prefix = "crawler.enrichment")
public interface EnrichmentConfig {

    List<ProviderConfig> providers();
}
