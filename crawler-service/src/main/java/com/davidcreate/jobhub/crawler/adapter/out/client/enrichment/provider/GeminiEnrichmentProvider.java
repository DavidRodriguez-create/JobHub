package com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider;

import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.GeminiClient;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.config.ProviderConfig;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.CurrencyConverter;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.EnrichmentParser;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.EnrichmentPrompt;
import com.davidcreate.jobhub.crawler.domain.exception.EnrichmentUnavailableException;
import com.davidcreate.jobhub.crawler.domain.model.JobEnrichment;
import com.davidcreate.jobhub.gemini.contract.model.Candidate;
import com.davidcreate.jobhub.gemini.contract.model.Content;
import com.davidcreate.jobhub.gemini.contract.model.GenerateContentRequest;
import com.davidcreate.jobhub.gemini.contract.model.GenerateContentResponse;
import com.davidcreate.jobhub.gemini.contract.model.GenerationConfig;
import com.davidcreate.jobhub.gemini.contract.model.Part;
import com.davidcreate.jobhub.gemini.contract.model.UsageMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enrichment via Google's Generative Language API.
 *
 * <p>It tries an ordered list of models ({@code config.models()}). Each free-tier
 * model has its own daily request cap, so when one model fails (typically a 429
 * once its quota is spent) that model is paused for a cooldown and the next model
 * in the list is used; only when every model is exhausted does it throw, letting
 * {@link com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.ProviderChainJobEnricher}
 * try the next provider.
 *
 * <p>Gemma models are supported too: on the Gemini API they accept neither a
 * {@code systemInstruction} nor JSON mode, so for a {@code gemma*} model the system
 * prompt is folded into the user turn and any markdown code fence is stripped from
 * the reply. Output still flows through the shared {@link EnrichmentParser}.
 */
public class GeminiEnrichmentProvider implements EnrichmentProvider {

    private static final Logger LOG = Logger.getLogger(GeminiEnrichmentProvider.class);

    private final ProviderConfig config;
    private final GeminiClient client;
    private final ObjectMapper objectMapper;
    private final CurrencyConverter converter;
    private final List<String> models;
    private final long cooldownMillis;
    private final int unusableReplyStrikes;

    // model -> epoch millis at which it may be retried; absent means healthy.
    private final Map<String, Long> modelRetryAt = new ConcurrentHashMap<>();

    // model -> consecutive unusable (reachable, unparseable) replies. Reset on a
    // successful parse; once it hits unusableReplyStrikes the model enters the
    // same cooldown a transport failure would trigger.
    private final Map<String, Integer> unusableReplyStreak = new ConcurrentHashMap<>();

    private static final int MAX_LOGGED_REPLY_CHARS = 500;

    public GeminiEnrichmentProvider(ProviderConfig config,
                                     GeminiClient client,
                                     ObjectMapper objectMapper,
                                     CurrencyConverter converter) {
        this.config = config;
        this.client = client;
        this.objectMapper = objectMapper;
        this.converter = converter;
        this.models = config.models().stream().map(String::trim).filter(m -> !m.isBlank()).toList();
        this.cooldownMillis = config.cooldownMinutes() * 60_000L;
        this.unusableReplyStrikes = config.unusableReplyStrikes();
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public JobEnrichment enrich(String title, String description, String city, String country) {
        boolean anyGenuineContentFailure = false;
        for (String model : models) {
            if (inCooldown(model)) {
                continue;
            }
            GenerateContentResponse response;
            try {
                response = client.generateContent(model, config.apiKey(),
                        buildRequest(model, title, description, city, country));
            } catch (Exception e) {
                enterCooldown(model, e);
                continue;
            }
            clearCooldown(model);
            logUsage(model, response);

            JobEnrichment enrichment = tryParse(model, response);
            if (enrichment != null) {
                unusableReplyStreak.remove(model);
                return enrichment;
            }
            // The model was reachable but its output was unusable. A handful of
            // unusable replies in a row is treated the same as a transport failure
            // (Gemma models can settle into always replying in prose); short of
            // that, try the next model rather than cooling it down.
            if (recordUnusableReplyAndCheckStrikeout(model)) {
                enterCooldown(model, new IllegalStateException("unusable output " + unusableReplyStrikes + " times in a row"));
            } else {
                anyGenuineContentFailure = true;
            }
        }
        if (anyGenuineContentFailure) {
            throw new IllegalStateException("No " + config.name() + " model produced a usable response");
        }
        throw new EnrichmentUnavailableException(
                "No " + config.name() + " model was reachable (transport failure, cooldown or quota exhaustion)");
    }

    private GenerateContentRequest buildRequest(String model, String title, String description,
                                                  String city, String country) {
        String userPrompt = EnrichmentPrompt.buildUserPrompt(title, description, city, country);
        GenerationConfig generationConfig = new GenerationConfig().temperature(0.0);

        if (isGemma(model)) {
            // No systemInstruction / no JSON mode: fold the system prompt into the user
            // turn and rely on the schema instructions to keep the reply JSON-only.
            return new GenerateContentRequest()
                    .contents(List.of(new Content()
                            .role("user")
                            .parts(List.of(new Part().text(
                                    EnrichmentPrompt.SYSTEM_PROMPT + "\n\n" + userPrompt)))))
                    .generationConfig(generationConfig);
        }
        return new GenerateContentRequest()
                .systemInstruction(new Content()
                        .parts(List.of(new Part().text(EnrichmentPrompt.SYSTEM_PROMPT))))
                .contents(List.of(new Content()
                        .role("user")
                        .parts(List.of(new Part().text(userPrompt)))))
                .generationConfig(generationConfig.responseMimeType("application/json"));
    }

    private JobEnrichment tryParse(String model, GenerateContentResponse response) {
        String content = extractText(response);
        String json = extractJsonObject(content);
        if (json == null) {
            LOG.warnf("%s model %s returned no parseable JSON object — raw reply: %s",
                    config.name(), model, flattenAndTruncate(content));
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            return EnrichmentParser.parse(node, converter);
        } catch (JsonProcessingException e) {
            LOG.warnf("%s model %s returned invalid JSON — raw reply: %s",
                    config.name(), model, flattenAndTruncate(content));
            return null;
        }
    }

    /**
     * Collapse embedded newlines so a raw model reply stays on one WARN log line,
     * then truncate to {@link #MAX_LOGGED_REPLY_CHARS} to keep pathological replies
     * out of the logs.
     */
    static String flattenAndTruncate(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
        return flat.length() > MAX_LOGGED_REPLY_CHARS ? flat.substring(0, MAX_LOGGED_REPLY_CHARS) : flat;
    }

    /**
     * @return true if this reply pushed the model's consecutive-unusable-reply
     * streak to the configured strike limit (caller should now enter cooldown).
     */
    private boolean recordUnusableReplyAndCheckStrikeout(String model) {
        int streak = unusableReplyStreak.merge(model, 1, Integer::sum);
        return streak >= unusableReplyStrikes;
    }

    private static boolean isGemma(String model) {
        return model.toLowerCase().startsWith("gemma");
    }

    /**
     * Pull the first balanced JSON object out of a reply. JSON-mode models return the
     * object alone, but a Gemma *thinking* model prefixes reasoning prose and wraps the
     * JSON in a ```json fence — so we scan for the first {@code {…}} span, tracking string
     * literals/escapes so braces inside strings don't throw the depth count off. Returns
     * null if there is no balanced object (e.g. a truncated reply).
     */
    static String extractJsonObject(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return text.substring(start, i + 1);
            }
        }
        return null;
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
        unusableReplyStreak.remove(model);
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

    private void logUsage(String model, GenerateContentResponse response) {
        if (response == null || !LOG.isInfoEnabled()) {
            return;
        }
        UsageMetadata usage = response.getUsageMetadata();
        String finishReason = response.getCandidates() == null || response.getCandidates().isEmpty()
                ? null : response.getCandidates().get(0).getFinishReason();
        LOG.infof("%s enrich model=%s in=%s out=%s total=%s done=%s",
                config.name(),
                model,
                usage == null ? null : usage.getPromptTokenCount(),
                usage == null ? null : usage.getCandidatesTokenCount(),
                usage == null ? null : usage.getTotalTokenCount(),
                finishReason);
    }

    private static String extractText(GenerateContentResponse response) {
        if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()) {
            return null;
        }
        Candidate candidate = response.getCandidates().get(0);
        if (candidate.getContent() == null || candidate.getContent().getParts() == null
                || candidate.getContent().getParts().isEmpty()) {
            return null;
        }
        return candidate.getContent().getParts().get(0).getText();
    }
}
