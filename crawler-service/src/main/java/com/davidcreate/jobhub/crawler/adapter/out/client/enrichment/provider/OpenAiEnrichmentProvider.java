package com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider;

import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.OpenAiClient;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.config.ProviderConfig;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.CurrencyConverter;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.EnrichmentParser;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.EnrichmentPrompt;
import com.davidcreate.jobhub.crawler.domain.model.JobEnrichment;
import com.davidcreate.jobhub.openai.contract.model.ChatCompletionChoice;
import com.davidcreate.jobhub.openai.contract.model.ChatCompletionMessage;
import com.davidcreate.jobhub.openai.contract.model.ChatCompletionRequest;
import com.davidcreate.jobhub.openai.contract.model.ChatCompletionResponse;
import com.davidcreate.jobhub.openai.contract.model.ChatCompletionResponseFormat;
import com.davidcreate.jobhub.openai.contract.model.ChatCompletionUsage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enrichment via an OpenAI-compatible chat-completions API (DeepSeek, Groq,
 * Together, Mistral, etc. — {@code POST /v1/chat/completions},
 * {@code Authorization: Bearer <api-key>}).
 *
 * <p>Mirrors {@link GeminiEnrichmentProvider}'s model-chain and per-model
 * cooldown behavior: tries {@code config.models()} in order, pausing a model
 * that fails for {@code config.cooldownMinutes()} (0 = no cooldown) and moving
 * to the next; throws once every model is exhausted so
 * {@link com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.ProviderChainJobEnricher}
 * tries the next provider.
 */
public class OpenAiEnrichmentProvider implements EnrichmentProvider {

    private static final Logger LOG = Logger.getLogger(OpenAiEnrichmentProvider.class);

    private final ProviderConfig config;
    private final OpenAiClient client;
    private final ObjectMapper objectMapper;
    private final CurrencyConverter converter;
    private final List<String> models;
    private final String bearerToken;
    private final long cooldownMillis;

    // model -> epoch millis at which it may be retried; absent means healthy.
    private final Map<String, Long> modelRetryAt = new ConcurrentHashMap<>();

    public OpenAiEnrichmentProvider(ProviderConfig config,
                                     OpenAiClient client,
                                     ObjectMapper objectMapper,
                                     CurrencyConverter converter) {
        this.config = config;
        this.client = client;
        this.objectMapper = objectMapper;
        this.converter = converter;
        this.models = config.models().stream().map(String::trim).filter(m -> !m.isBlank()).toList();
        this.bearerToken = "Bearer " + config.apiKey();
        this.cooldownMillis = config.cooldownMinutes() * 60_000L;
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public JobEnrichment enrich(String title, String description, String city, String country) {
        for (String model : models) {
            if (inCooldown(model)) {
                continue;
            }
            ChatCompletionResponse response;
            try {
                response = client.complete(bearerToken, buildRequest(model, title, description, city, country));
            } catch (Exception e) {
                enterCooldown(model, e);
                continue;
            }
            clearCooldown(model);
            logUsage(model, response);

            JobEnrichment enrichment = tryParse(model, response);
            if (enrichment != null) {
                return enrichment;
            }
            // The model was reachable but its output was unusable — try the next one
            // rather than cool it down (this is not a quota problem).
        }
        throw new IllegalStateException("No " + config.name() + " model produced a usable response");
    }

    private ChatCompletionRequest buildRequest(String model, String title, String description,
                                                 String city, String country) {
        String userPrompt = EnrichmentPrompt.buildUserPrompt(title, description, city, country);
        return new ChatCompletionRequest()
                .model(model)
                .messages(List.of(
                        new ChatCompletionMessage().role("system").content(EnrichmentPrompt.SYSTEM_PROMPT),
                        new ChatCompletionMessage().role("user").content(userPrompt)))
                .temperature(0.0)
                .responseFormat(new ChatCompletionResponseFormat().type("json_object"));
    }

    private JobEnrichment tryParse(String model, ChatCompletionResponse response) {
        String content = extractContent(response);
        String json = GeminiEnrichmentProvider.extractJsonObject(content);
        if (json == null) {
            LOG.warnf("%s model %s returned no parseable JSON object", config.name(), model);
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return EnrichmentParser.parse(node, converter);
        } catch (JsonProcessingException e) {
            LOG.warnf("%s model %s returned invalid JSON", config.name(), model);
            return null;
        }
    }

    private static String extractContent(ChatCompletionResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return null;
        }
        ChatCompletionChoice choice = response.getChoices().get(0);
        if (choice.getMessage() == null) {
            return null;
        }
        return choice.getMessage().getContent();
    }

    // ─── Per-model cooldown ───────────────────────────────────────────────────

    private boolean inCooldown(String model) {
        Long retryAt = modelRetryAt.get(model);
        if (retryAt == null) {
            return false;
        }
        if (cooldownMillis <= 0 || System.currentTimeMillis() >= retryAt) {
            LOG.infof("%s model %s cooldown elapsed — retrying.", config.name(), model);
            modelRetryAt.remove(model);
            return false;
        }
        return true;
    }

    private void enterCooldown(String model, Exception e) {
        if (cooldownMillis <= 0) {
            LOG.warnf("%s model %s failed (%s) — no cooldown configured, moving on.",
                    config.name(), model, e.getMessage());
            return;
        }
        boolean wasHealthy = !modelRetryAt.containsKey(model);
        modelRetryAt.put(model, System.currentTimeMillis() + cooldownMillis);
        if (wasHealthy) {
            LOG.warnf("%s model %s failed (%s) — pausing it for %d min and moving on.",
                    config.name(), model, e.getMessage(), cooldownMillis / 60_000L);
        }
    }

    private void clearCooldown(String model) {
        if (modelRetryAt.remove(model) != null) {
            LOG.infof("%s model %s recovered.", config.name(), model);
        }
    }

    private void logUsage(String model, ChatCompletionResponse response) {
        if (response == null || !LOG.isInfoEnabled()) {
            return;
        }
        ChatCompletionUsage usage = response.getUsage();
        String finishReason = response.getChoices() == null || response.getChoices().isEmpty()
                ? null : response.getChoices().get(0).getFinishReason();
        LOG.infof("%s enrich model=%s in=%s out=%s total=%s done=%s",
                config.name(),
                model,
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                usage == null ? null : usage.getTotalTokens(),
                finishReason);
    }
}
