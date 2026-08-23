package com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.config;

import org.eclipse.microprofile.config.spi.Converter;

/**
 * Passes the raw config value through unchanged, including an empty string.
 *
 * <p>SmallRye Config's built-in {@code String} converter treats an empty-string
 * value as absent and raises {@code SRCFG00040} for a {@code @ConfigMapping}
 * property such as {@code crawler.enrichment.providers[N].api-key} that legitimately
 * resolves to {@code ""} (e.g. {@code ${GEMINI_API_KEY:}} with no env var set, or
 * Ollama's blank key — see ADR 0004). Applying this converter via
 * {@code @WithConverter} on {@link ProviderConfig#apiKey()} keeps the property's
 * type as plain {@code String} (consumed as {@code ""} by
 * {@code EnrichmentProviderFactory.hasRequiredKey}) without that special-casing.
 */
public class IdentityStringConverter implements Converter<String> {

    @Override
    public String convert(String value) {
        return value;
    }
}
