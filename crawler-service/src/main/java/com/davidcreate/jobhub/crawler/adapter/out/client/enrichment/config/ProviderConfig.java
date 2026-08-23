package com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.config;

import io.smallrye.config.WithConverter;
import io.smallrye.config.WithDefault;

import java.util.List;

/**
 * One entry in {@code crawler.enrichment.providers[N]}. Each entry is tried in
 * declaration order by {@link com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.ProviderChainJobEnricher}.
 */
public interface ProviderConfig {

    String name();

    ProviderType type();

    String baseUrl();

    @WithDefault("")
    @WithConverter(IdentityStringConverter.class)
    String apiKey();

    List<String> models();

    @WithDefault("30")
    int cooldownMinutes();

    @WithDefault("30000")
    int readTimeoutMs();

    @WithDefault("true")
    boolean enabled();

    /**
     * Consecutive unusable replies (reachable, but no parseable JSON object) a
     * model may return before it enters the same cooldown a transport failure
     * would trigger. A successful parse resets the count to zero.
     */
    @WithDefault("3")
    int unusableReplyStrikes();
}
