package com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider;

import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.OllamaClient;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.config.ProviderConfig;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.CurrencyConverter;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.EnrichmentParser;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.EnrichmentPrompt;
import com.davidcreate.jobhub.crawler.domain.model.JobEnrichment;
import com.davidcreate.jobhub.ollama.contract.model.ChatMessage;
import com.davidcreate.jobhub.ollama.contract.model.ChatOptions;
import com.davidcreate.jobhub.ollama.contract.model.ChatRequest;
import com.davidcreate.jobhub.ollama.contract.model.ChatResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Enrichment via a local Ollama server. Runs on the deployment's own CPU, opt-in
 * per ADR 0004. Single-model: only the first entry of {@code config.models()} is
 * used. No cooldown — local, no rate limits.
 */
public class OllamaEnrichmentProvider implements EnrichmentProvider {

    private static final Logger LOG = Logger.getLogger(OllamaEnrichmentProvider.class);

    private final ProviderConfig config;
    private final OllamaClient client;
    private final ObjectMapper objectMapper;
    private final CurrencyConverter converter;
    private final String model;

    public OllamaEnrichmentProvider(ProviderConfig config,
                                     OllamaClient client,
                                     ObjectMapper objectMapper,
                                     CurrencyConverter converter) {
        this.config = config;
        this.client = client;
        this.objectMapper = objectMapper;
        this.converter = converter;
        this.model = config.models().get(0);
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public JobEnrichment enrich(String title, String description, String city, String country) {
        ChatRequest request = new ChatRequest()
                .model(model)
                .stream(false)
                .format("json")
                .options(new ChatOptions().temperature(0.0))
                .messages(List.of(
                        new ChatMessage().role("system").content(EnrichmentPrompt.SYSTEM_PROMPT),
                        new ChatMessage().role("user").content(EnrichmentPrompt.buildUserPrompt(title, description, city, country))));

        ChatResponse response = client.chat(request);
        logUsage(response);
        String content = response == null || response.getMessage() == null ? null : response.getMessage().getContent();
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Ollama returned an empty response");
        }

        try {
            JsonNode node = objectMapper.readTree(content);
            return EnrichmentParser.parse(node, converter);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ollama returned non-JSON content", e);
        }
    }

    private void logUsage(ChatResponse response) {
        if (response == null || !LOG.isInfoEnabled()) {
            return;
        }
        Long total = response.getTotalDuration();
        LOG.infof("ollama enrich model=%s in=%s out=%s done=%s %.1fs",
                model, response.getPromptEvalCount(), response.getEvalCount(),
                response.getDoneReason(), total == null ? 0d : total / 1e9);
    }
}
