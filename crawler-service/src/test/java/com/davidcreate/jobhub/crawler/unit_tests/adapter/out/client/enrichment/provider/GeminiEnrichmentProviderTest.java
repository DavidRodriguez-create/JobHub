package com.davidcreate.jobhub.crawler.unit_tests.adapter.out.client.enrichment.provider;

import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.GeminiClient;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.config.ProviderConfig;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.config.ProviderType;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider.GeminiEnrichmentProvider;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.CurrencyConverter;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.EnrichmentPrompt;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.FxRateConfig;
import com.davidcreate.jobhub.crawler.domain.exception.EnrichmentUnavailableException;
import com.davidcreate.jobhub.crawler.domain.model.JobEnrichment;
import com.davidcreate.jobhub.gemini.contract.model.Candidate;
import com.davidcreate.jobhub.gemini.contract.model.Content;
import com.davidcreate.jobhub.gemini.contract.model.GenerateContentRequest;
import com.davidcreate.jobhub.gemini.contract.model.GenerateContentResponse;
import com.davidcreate.jobhub.gemini.contract.model.Part;
import com.davidcreate.jobhub.gemini.contract.model.UsageMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GeminiEnrichmentProvider Unit Tests")
class GeminiEnrichmentProviderTest {

    private static final String VALID_JSON = """
            {"employmentType":"full-time","careerLevel":"senior","languages":["English"],
             "requirements":["Go"],"city":"London","country":"United Kingdom","remote":false,
             "compensationMin":null,"compensationMax":null,"currency":null}""";

    private static final FxRateConfig RATES = new FxRateConfig() {
        public BigDecimal eur() { return BigDecimal.ONE; }
        public BigDecimal gbp() { return new BigDecimal("1.17"); }
        public BigDecimal usd() { return new BigDecimal("0.92"); }
        public BigDecimal chf() { return new BigDecimal("1.04"); }
        public BigDecimal sek() { return new BigDecimal("0.087"); }
        public BigDecimal nok() { return new BigDecimal("0.086"); }
        public BigDecimal dkk() { return new BigDecimal("0.134"); }
        public BigDecimal pln() { return new BigDecimal("0.23"); }
        public BigDecimal cad() { return new BigDecimal("0.68"); }
    };

    @Mock
    GeminiClient client;

    private CurrencyConverter converter;

    private final List<LogRecord> capturedLogs = new ArrayList<>();
    private java.util.logging.Logger julLogger;
    private Handler captureHandler;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<CurrencyConverter> c = CurrencyConverter.class.getDeclaredConstructor(FxRateConfig.class);
        c.setAccessible(true);
        converter = c.newInstance(RATES);

        capturedLogs.clear();
        julLogger = java.util.logging.Logger.getLogger(GeminiEnrichmentProvider.class.getName());
        julLogger.setLevel(Level.ALL);
        captureHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                capturedLogs.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        julLogger.addHandler(captureHandler);
    }

    @AfterEach
    void tearDown() {
        julLogger.removeHandler(captureHandler);
    }

    private String formattedLogMessages() {
        StringBuilder sb = new StringBuilder();
        for (LogRecord record : capturedLogs) {
            String msg = record.getMessage();
            Object[] params = record.getParameters();
            if (params != null && params.length > 0) {
                try {
                    msg = String.format(msg, params);
                } catch (Exception ignored) {
                    // message was already formatted by the caller
                }
            }
            sb.append(msg).append('\n');
        }
        return sb.toString();
    }

    private static ProviderConfig configOf(String name, String apiKey, List<String> models, int cooldownMinutes) {
        return configOf(name, apiKey, models, cooldownMinutes, 3);
    }

    private static ProviderConfig configOf(String name, String apiKey, List<String> models, int cooldownMinutes,
                                            int unusableReplyStrikes) {
        return new ProviderConfig() {
            public String name() { return name; }
            public ProviderType type() { return ProviderType.GEMINI; }
            public String baseUrl() { return "https://generativelanguage.googleapis.com"; }
            public String apiKey() { return apiKey; }
            public List<String> models() { return models; }
            public int cooldownMinutes() { return cooldownMinutes; }
            public int readTimeoutMs() { return 90000; }
            public boolean enabled() { return true; }
            public int unusableReplyStrikes() { return unusableReplyStrikes; }
        };
    }

    private GeminiEnrichmentProvider provider(List<String> models) {
        return new GeminiEnrichmentProvider(configOf("gemini", "test-key", models, 30), client, new ObjectMapper(), converter);
    }

    private GeminiEnrichmentProvider provider(List<String> models, String apiKey) {
        return new GeminiEnrichmentProvider(configOf("gemini", apiKey, models, 30), client, new ObjectMapper(), converter);
    }

    private GeminiEnrichmentProvider provider(List<String> models, int cooldownMinutes, int unusableReplyStrikes) {
        return new GeminiEnrichmentProvider(
                configOf("gemini", "test-key", models, cooldownMinutes, unusableReplyStrikes),
                client, new ObjectMapper(), converter);
    }

    private static GenerateContentResponse responseWith(String text) {
        return new GenerateContentResponse()
                .candidates(List.of(new Candidate()
                        .content(new Content().parts(List.of(new Part().text(text))))
                        .finishReason("STOP")))
                .usageMetadata(new UsageMetadata()
                        .promptTokenCount(100).candidatesTokenCount(20).totalTokenCount(120));
    }

    private JobEnrichment enrichSample(GeminiEnrichmentProvider provider) {
        return provider.enrich("Engineer", "Builds things in London.", "London", "United Kingdom");
    }

    @Test
    @DisplayName("TC-GEM-01: rolls to the next model when the first fails, returning its result")
    void rollsToNextModelOnFailure() {
        when(client.generateContent(eq("model-a"), any(), any()))
                .thenThrow(new RuntimeException("429 Too Many Requests"));
        when(client.generateContent(eq("model-b"), any(), any()))
                .thenReturn(responseWith(VALID_JSON));

        JobEnrichment result = enrichSample(provider(List.of("model-a", "model-b")));

        assertThat(result).isNotNull();
        assertThat(result.careerLevel()).isEqualTo("senior");
        verify(client).generateContent(eq("model-a"), any(), any());
        verify(client).generateContent(eq("model-b"), any(), any());
    }

    @Test
    @DisplayName("TC-GEM-02: skips a model that is still in cooldown on a subsequent call")
    void skipsModelInCooldown() {
        when(client.generateContent(eq("model-a"), any(), any()))
                .thenThrow(new RuntimeException("429 Too Many Requests"));
        when(client.generateContent(eq("model-b"), any(), any()))
                .thenReturn(responseWith(VALID_JSON));

        GeminiEnrichmentProvider provider = provider(List.of("model-a", "model-b"));
        enrichSample(provider); // model-a fails -> cooldown, model-b serves
        enrichSample(provider); // model-a is cooling down -> skipped, model-b serves again

        verify(client, times(1)).generateContent(eq("model-a"), any(), any());
        verify(client, times(2)).generateContent(eq("model-b"), any(), any());
    }

    @Test
    @DisplayName("TC-GEM-03 (#556): throws EnrichmentUnavailableException when every model fails transiently")
    void throwsWhenAllModelsFail() {
        when(client.generateContent(any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        GeminiEnrichmentProvider provider = provider(List.of("model-a", "model-b"));

        assertThatThrownBy(() -> enrichSample(provider))
                .isInstanceOf(EnrichmentUnavailableException.class);
        verify(client).generateContent(eq("model-a"), any(), any());
        verify(client).generateContent(eq("model-b"), any(), any());
    }

    @Test
    @DisplayName("TC-GEM-04: Gemma model request shape — no systemInstruction, no JSON mime type, system prompt folded into user turn")
    void gemmaRequestShape() {
        ArgumentCaptor<GenerateContentRequest> captor = ArgumentCaptor.forClass(GenerateContentRequest.class);
        when(client.generateContent(eq("gemma-4-26b-it"), any(), captor.capture()))
                .thenReturn(responseWith(VALID_JSON));

        enrichSample(provider(List.of("gemma-4-26b-it")));

        GenerateContentRequest req = captor.getValue();
        assertThat(req.getSystemInstruction()).isNull();
        assertThat(req.getGenerationConfig().getResponseMimeType()).isNull();
        assertThat(req.getContents().get(0).getParts().get(0).getText())
                .contains(EnrichmentPrompt.SYSTEM_PROMPT);
    }

    @Test
    @DisplayName("TC-GEM-05: non-Gemma (Gemini) model request shape — systemInstruction present, JSON mime type set")
    void geminiRequestShape() {
        ArgumentCaptor<GenerateContentRequest> captor = ArgumentCaptor.forClass(GenerateContentRequest.class);
        when(client.generateContent(eq("gemini-3.1-flash-lite"), any(), captor.capture()))
                .thenReturn(responseWith(VALID_JSON));

        enrichSample(provider(List.of("gemini-3.1-flash-lite")));

        GenerateContentRequest req = captor.getValue();
        assertThat(req.getSystemInstruction()).isNotNull();
        assertThat(req.getGenerationConfig().getResponseMimeType()).isEqualTo("application/json");
    }

    @Test
    @DisplayName("TC-GEM-06: parses a Gemma thinking reply — reasoning prose + fenced ```json object")
    void parsesGemmaThinkingReply() {
        String reply = "*   Task: extract job facts.\n"
                + "*   careerLevel: \"Senior\" -> senior\n\n"
                + "```json\n" + VALID_JSON + "\n```";
        when(client.generateContent(eq("gemma-4-31b-it"), any(), any()))
                .thenReturn(responseWith(reply));

        JobEnrichment result = enrichSample(provider(List.of("gemma-4-31b-it")));

        assertThat(result).isNotNull();
        assertThat(result.employmentType()).isEqualTo("full-time");
        assertThat(result.city()).isEqualTo("London");
    }

    @Test
    @DisplayName("TC-GEM-07: extracts a balanced JSON object amid prose even when string values contain literal braces")
    void handlesBracesInsideStrings() {
        String json = "{\"employmentType\":\"full-time\",\"careerLevel\":\"senior\","
                + "\"requirements\":[\"C++ templates {weird}\"],\"city\":\"London\",\"country\":\"UK\"}";
        when(client.generateContent(eq("gemini-3.1-flash-lite"), any(), any()))
                .thenReturn(responseWith("here you go:\n" + json + "\nhope that helps"));

        JobEnrichment result = enrichSample(provider(List.of("gemini-3.1-flash-lite")));

        assertThat(result).isNotNull();
        assertThat(result.requirements()).containsExactly("C++ templates {weird}");
        assertThat(result.city()).isEqualTo("London");
    }

    @Test
    @DisplayName("TC-GEM-08: a truncated/unbalanced reply is unusable — provider throws so the chain moves on")
    void truncatedReplyThrows() {
        when(client.generateContent(any(), any(), any()))
                .thenReturn(responseWith("{\"employmentType\": \"full-time\"")); // no closing brace

        assertThatThrownBy(() -> enrichSample(provider(List.of("m1"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("TC-GEM-09: language normalization — variant mapped, programming language dropped, unknown human language -> Unknown")
    void normalizesLanguages() {
        String json = """
                {"employmentType":"full-time","careerLevel":"mid",
                 "languages":["Deutsch","Python","Swahili","en"],
                 "requirements":[],"city":null,"country":null,"remote":false,
                 "compensationMin":null,"compensationMax":null,"currency":null}""";
        when(client.generateContent(eq("gemini-3.1-flash-lite"), any(), any()))
                .thenReturn(responseWith(json));

        JobEnrichment result = enrichSample(provider(List.of("gemini-3.1-flash-lite")));

        // "Deutsch" -> "German", "Python" -> dropped, "Swahili" -> "Unknown", "en" -> "English"
        assertThat(result.languages()).containsExactly("German", "Unknown", "English");
    }

    @Test
    @DisplayName("TC-GEM-10: name() returns the configured name for chain logging")
    void nameReturnsConfiguredName() {
        GeminiEnrichmentProvider provider = provider(List.of("gemini-3.1-flash-lite"));

        assertThat(provider.name()).isEqualTo("gemini");
    }

    @Test
    @DisplayName("TC-GEM-11: blank/no apiKey is the factory's concern — provider attempts the call without an internal guard")
    void blankApiKeyDoesNotGuardInternally() {
        when(client.generateContent(eq("model-a"), any(), any()))
                .thenReturn(responseWith(VALID_JSON));

        JobEnrichment result = enrichSample(provider(List.of("model-a"), ""));

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("C1: unusable reply logs the raw text (truncated to ~500 chars, one line) next to the existing WARN")
    void logsRawReplyOnUnusableOutput() {
        String prose = "I'm sorry, I cannot extract structured data from this posting.";
        when(client.generateContent(eq("model-a"), any(), any()))
                .thenReturn(responseWith(prose));
        when(client.generateContent(eq("model-b"), any(), any()))
                .thenReturn(responseWith(VALID_JSON));

        enrichSample(provider(List.of("model-a", "model-b")));

        String logs = formattedLogMessages();
        assertThat(logs).contains("no parseable JSON object");
        assertThat(logs).contains(prose);
    }

    @Test
    @DisplayName("C2: 3 consecutive unusable replies (default strikes) trip cooldown; 4th call is skipped")
    void unusableRepliesTripCooldownAtDefaultStrikeCount() {
        when(client.generateContent(eq("model-a"), any(), any()))
                .thenReturn(responseWith("not json, just prose."));
        when(client.generateContent(eq("model-b"), any(), any()))
                .thenReturn(responseWith(VALID_JSON));

        GeminiEnrichmentProvider provider = provider(List.of("model-a", "model-b"), 30, 3);
        enrichSample(provider); // strike 1
        enrichSample(provider); // strike 2
        enrichSample(provider); // strike 3 -> cooldown
        enrichSample(provider); // model-a should be skipped now

        verify(client, times(3)).generateContent(eq("model-a"), any(), any());
        verify(client, times(4)).generateContent(eq("model-b"), any(), any());
    }

    @Test
    @DisplayName("C3: a valid parse resets the strike counter to zero")
    void validParseResetsStrikeCounter() {
        when(client.generateContent(eq("model-a"), any(), any()))
                .thenReturn(responseWith("not json, just prose."))
                .thenReturn(responseWith("not json, just prose."))
                .thenReturn(responseWith(VALID_JSON))
                .thenReturn(responseWith("not json, just prose."))
                .thenReturn(responseWith("not json, just prose."))
                .thenReturn(responseWith("not json, just prose."));
        when(client.generateContent(eq("model-b"), any(), any()))
                .thenReturn(responseWith(VALID_JSON));

        GeminiEnrichmentProvider provider = provider(List.of("model-a", "model-b"), 30, 3);
        enrichSample(provider); // strike 1 (model-b serves)
        enrichSample(provider); // strike 2 (model-b serves)
        JobEnrichment result = enrichSample(provider); // model-a valid parse -> reset, returned directly
        assertThat(result).isNotNull();
        enrichSample(provider); // strike 1 post-reset (model-b serves)
        enrichSample(provider); // strike 2 post-reset (model-b serves)
        enrichSample(provider); // strike 3 post-reset -> cooldown enters, model-b still serves

        verify(client, times(6)).generateContent(eq("model-a"), any(), any());
        // 4 calls served by model-b (strikes 1,2 pre-reset + strikes 1,2 post-reset);
        // the 3rd call was served by model-a itself, the 6th (strike 3) also falls
        // through to model-b since model-a enters cooldown rather than being retried.
        verify(client, times(5)).generateContent(eq("model-b"), any(), any());
    }

    @Test
    @DisplayName("C4: unusable-reply-strikes=1 trips cooldown immediately")
    void configurableSingleStrikeTripsCooldownImmediately() {
        when(client.generateContent(eq("model-a"), any(), any()))
                .thenReturn(responseWith("not json, just prose."));
        when(client.generateContent(eq("model-b"), any(), any()))
                .thenReturn(responseWith(VALID_JSON));

        GeminiEnrichmentProvider provider = provider(List.of("model-a", "model-b"), 30, 1);
        enrichSample(provider); // 1 unusable reply -> cooldown immediately
        enrichSample(provider); // model-a should be skipped now

        verify(client, times(1)).generateContent(eq("model-a"), any(), any());
        verify(client, times(2)).generateContent(eq("model-b"), any(), any());
    }

    @Test
    @DisplayName("C5: model mid-cooldown from strikes is skipped exactly like a transport-failure cooldown")
    void modelMidStrikeCooldownIsSkipped() {
        when(client.generateContent(eq("model-a"), any(), any()))
                .thenReturn(responseWith("not json, just prose."));
        when(client.generateContent(eq("model-b"), any(), any()))
                .thenReturn(responseWith(VALID_JSON));

        GeminiEnrichmentProvider provider = provider(List.of("model-a", "model-b"), 30, 1);
        enrichSample(provider); // cooldown tripped
        enrichSample(provider); // still in cooldown -> skipped
        enrichSample(provider); // still in cooldown -> skipped

        verify(client, times(1)).generateContent(eq("model-a"), any(), any());
        verify(client, times(3)).generateContent(eq("model-b"), any(), any());
    }

    @Test
    @DisplayName("C6: transport exception cooldown path is unaffected by the unusable-reply strike counter")
    void transportFailureCooldownUnaffectedByStrikeCounter() {
        when(client.generateContent(eq("model-a"), any(), any()))
                .thenThrow(new RuntimeException("429 Too Many Requests"));
        when(client.generateContent(eq("model-b"), any(), any()))
                .thenReturn(responseWith(VALID_JSON));

        GeminiEnrichmentProvider provider = provider(List.of("model-a", "model-b"), 30, 3);
        enrichSample(provider); // 1 transport failure -> cooldown immediately (existing behaviour)
        enrichSample(provider); // model-a still in cooldown -> skipped

        verify(client, times(1)).generateContent(eq("model-a"), any(), any());
        verify(client, times(2)).generateContent(eq("model-b"), any(), any());
    }

    @Test
    @DisplayName("C15: a raw reply shorter than 500 chars is logged verbatim, not truncated")
    void shortRawReplyLoggedVerbatim() {
        String prose = "short prose reply.";
        when(client.generateContent(eq("model-a"), any(), any()))
                .thenReturn(responseWith(prose));
        when(client.generateContent(eq("model-b"), any(), any()))
                .thenReturn(responseWith(VALID_JSON));

        enrichSample(provider(List.of("model-a", "model-b")));

        String logs = formattedLogMessages();
        assertThat(logs).contains(prose);
        assertThat(logs).doesNotContain("...");
    }

    @Test
    @DisplayName("C16: embedded newlines in the raw reply are stripped so the log line stays single-line")
    void newlinesInRawReplyAreCollapsed() {
        String prose = "line one\nline two\nline three";
        when(client.generateContent(eq("model-a"), any(), any()))
                .thenReturn(responseWith(prose));
        when(client.generateContent(eq("model-b"), any(), any()))
                .thenReturn(responseWith(VALID_JSON));

        enrichSample(provider(List.of("model-a", "model-b")));

        String logged = capturedLogs.stream()
                .map(r -> {
                    String msg = r.getMessage();
                    Object[] params = r.getParameters();
                    return params != null && params.length > 0 ? String.format(msg, params) : msg;
                })
                .filter(m -> m.contains("line one"))
                .findFirst()
                .orElseThrow();

        assertThat(logged).doesNotContain("\n");
        assertThat(logged).contains("line one");
        assertThat(logged).contains("line two");
        assertThat(logged).contains("line three");
    }
}
