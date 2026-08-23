package com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider;

import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.GeminiClient;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.OllamaClient;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.OpenAiClient;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.config.EnrichmentConfig;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.config.ProviderConfig;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.config.ProviderType;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.CurrencyConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.jboss.logging.Logger;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Builds the enabled+keyed {@link EnrichmentProvider}s from
 * {@code crawler.enrichment.providers[N]} configuration, in declaration order.
 * REST clients are created programmatically per provider (different providers
 * of the same type can target different base URLs), per ADR 0004 §4/§6.
 */
@ApplicationScoped
public class EnrichmentProviderFactory {

    private static final Logger LOG = Logger.getLogger(EnrichmentProviderFactory.class);

    private final ObjectMapper objectMapper;
    private final CurrencyConverter converter;

    public EnrichmentProviderFactory(ObjectMapper objectMapper, CurrencyConverter converter) {
        this.objectMapper = objectMapper;
        this.converter = converter;
    }

    public List<EnrichmentProvider> createProviders(List<ProviderConfig> configs) {
        return configs.stream()
                .filter(ProviderConfig::enabled)
                .filter(this::hasRequiredKey)
                .map(this::createProvider)
                .toList();
    }

    /**
     * Builds the enabled+keyed provider chain exactly ONCE per application, as an
     * {@code ApplicationScoped} CDI bean, injected by {@code ProviderChainJobEnricher}.
     */
    @Produces
    @ApplicationScoped
    public List<EnrichmentProvider> providers(EnrichmentConfig config) {
        return createProviders(config.providers());
    }

    private boolean hasRequiredKey(ProviderConfig config) {
        if (config.type() == ProviderType.OLLAMA) {
            return true;
        }
        boolean hasKey = config.apiKey() != null && !config.apiKey().isBlank();
        if (!hasKey) {
            LOG.warnf("Provider '%s' is enabled but has no API key — skipping.", config.name());
        }
        return hasKey;
    }

    private EnrichmentProvider createProvider(ProviderConfig config) {
        return switch (config.type()) {
            case GEMINI -> new GeminiEnrichmentProvider(config, buildClient(config, GeminiClient.class),
                    objectMapper, converter);
            case OPENAI -> new OpenAiEnrichmentProvider(config, buildClient(config, OpenAiClient.class),
                    objectMapper, converter);
            case OLLAMA -> new OllamaEnrichmentProvider(config, buildClient(config, OllamaClient.class),
                    objectMapper, converter);
        };
    }

    protected <T> T buildClient(ProviderConfig config, Class<T> iface) {
        return RestClientBuilder.newBuilder()
                .baseUri(URI.create(config.baseUrl()))
                .readTimeout(config.readTimeoutMs(), TimeUnit.MILLISECONDS)
                .build(iface);
    }
}
