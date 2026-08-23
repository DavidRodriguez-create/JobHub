package com.davidcreate.jobhub.crawler.unit_tests.adapter.out.client.enrichment.provider;

import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.OpenAiClient;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.config.ProviderConfig;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.config.ProviderType;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider.OpenAiEnrichmentProvider;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.CurrencyConverter;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.EnrichmentPrompt;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.FxRateConfig;
import com.davidcreate.jobhub.crawler.domain.model.JobEnrichment;
import com.davidcreate.jobhub.openai.contract.model.ChatCompletionChoice;
import com.davidcreate.jobhub.openai.contract.model.ChatCompletionMessage;
import com.davidcreate.jobhub.openai.contract.model.ChatCompletionRequest;
import com.davidcreate.jobhub.openai.contract.model.ChatCompletionResponse;
import com.davidcreate.jobhub.openai.contract.model.ChatCompletionUsage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OpenAiEnrichmentProvider Unit Tests")
class OpenAiEnrichmentProviderTest {

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
    OpenAiClient client;

    private CurrencyConverter converter;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<CurrencyConverter> c = CurrencyConverter.class.getDeclaredConstructor(FxRateConfig.class);
        c.setAccessible(true);
        converter = c.newInstance(RATES);
    }

    private static ProviderConfig configOf(List<String> models, int cooldownMinutes) {
        return new ProviderConfig() {
            public String name() { return "deepseek"; }
            public ProviderType type() { return ProviderType.OPENAI; }
            public String baseUrl() { return "https://api.deepseek.com"; }
            public String apiKey() { return "dsk-test-key"; }
            public List<String> models() { return models; }
            public int cooldownMinutes() { return cooldownMinutes; }
            public int readTimeoutMs() { return 60000; }
            public boolean enabled() { return true; }
            public int unusableReplyStrikes() { return 3; }
        };
    }

    private OpenAiEnrichmentProvider provider(List<String> models) {
        return provider(models, 30);
    }

    private OpenAiEnrichmentProvider provider(List<String> models, int cooldownMinutes) {
        return new OpenAiEnrichmentProvider(configOf(models, cooldownMinutes), client, new ObjectMapper(), converter);
    }

    private static ChatCompletionResponse responseWith(String content) {
        return new ChatCompletionResponse()
                .id("chatcmpl-test")
                .choices(List.of(new ChatCompletionChoice()
                        .index(0)
                        .message(new ChatCompletionMessage().role("assistant").content(content))
                        .finishReason("stop")))
                .usage(new ChatCompletionUsage().promptTokens(100).completionTokens(20).totalTokens(120));
    }

    private JobEnrichment enrichSample(OpenAiEnrichmentProvider provider) {
        return provider.enrich("Engineer", "Builds things in London.", "London", "United Kingdom");
    }

    private static org.mockito.ArgumentMatcher<ChatCompletionRequest> forModel(String model) {
        return req -> req != null && model.equals(req.getModel());
    }

    @Test
    @DisplayName("TC-OAI-01: successful single-model call returns a parsed JobEnrichment")
    void successfulSingleModelCall() {
        when(client.complete(eq("Bearer dsk-test-key"), argThat(forModel("deepseek-chat"))))
                .thenReturn(responseWith(VALID_JSON));

        JobEnrichment result = enrichSample(provider(List.of("deepseek-chat")));

        assertThat(result).isNotNull();
        assertThat(result.careerLevel()).isEqualTo("senior");
        assertThat(result.city()).isEqualTo("London");
        verify(client, times(1)).complete(any(), any());
    }

    @Test
    @DisplayName("TC-OAI-02: Authorization header is \"Bearer \" + apiKey")
    void authorizationHeaderIsBearerApiKey() {
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        when(client.complete(tokenCaptor.capture(), any()))
                .thenReturn(responseWith(VALID_JSON));

        enrichSample(provider(List.of("deepseek-chat")));

        assertThat(tokenCaptor.getValue()).isEqualTo("Bearer dsk-test-key");
    }

    @Test
    @DisplayName("TC-OAI-03: request shape — model, messages (system + user), temperature=0, response_format=json_object")
    void requestShape() {
        ArgumentCaptor<ChatCompletionRequest> captor = ArgumentCaptor.forClass(ChatCompletionRequest.class);
        when(client.complete(any(), captor.capture()))
                .thenReturn(responseWith(VALID_JSON));

        enrichSample(provider(List.of("deepseek-chat")));

        ChatCompletionRequest req = captor.getValue();
        assertThat(req.getModel()).isEqualTo("deepseek-chat");
        assertThat(req.getMessages()).hasSize(2);
        assertThat(req.getMessages().get(0).getRole()).isEqualTo("system");
        assertThat(req.getMessages().get(0).getContent()).contains(EnrichmentPrompt.SYSTEM_PROMPT);
        assertThat(req.getMessages().get(1).getRole()).isEqualTo("user");
        assertThat(req.getMessages().get(1).getContent()).contains("Engineer");
        assertThat(req.getTemperature()).isEqualTo(0.0);
        assertThat(req.getResponseFormat()).isNotNull();
        assertThat(req.getResponseFormat().getType()).isEqualTo("json_object");
    }

    @Test
    @DisplayName("TC-OAI-04: rolls to the next model when the first fails")
    void rollsToNextModelOnFailure() {
        when(client.complete(any(), argThat(forModel("deepseek-chat"))))
                .thenThrow(new RuntimeException("rate limited"));
        when(client.complete(any(), argThat(forModel("deepseek-reasoner"))))
                .thenReturn(responseWith(VALID_JSON));

        JobEnrichment result = enrichSample(provider(List.of("deepseek-chat", "deepseek-reasoner")));

        assertThat(result).isNotNull();
        verify(client).complete(any(), argThat(forModel("deepseek-chat")));
        verify(client).complete(any(), argThat(forModel("deepseek-reasoner")));
    }

    @Test
    @DisplayName("TC-OAI-05: per-model cooldown — second enrich() call skips the cooled-down model")
    void perModelCooldownSkipsOnSecondCall() {
        when(client.complete(any(), argThat(forModel("deepseek-chat"))))
                .thenThrow(new RuntimeException("rate limited"));
        when(client.complete(any(), argThat(forModel("deepseek-reasoner"))))
                .thenReturn(responseWith(VALID_JSON));

        OpenAiEnrichmentProvider provider = provider(List.of("deepseek-chat", "deepseek-reasoner"));
        enrichSample(provider);
        enrichSample(provider);

        verify(client, times(1)).complete(any(), argThat(forModel("deepseek-chat")));
        verify(client, times(2)).complete(any(), argThat(forModel("deepseek-reasoner")));
    }

    @Test
    @DisplayName("TC-OAI-06: all models fail -> IllegalStateException")
    void allModelsFailThrows() {
        when(client.complete(any(), any())).thenThrow(new RuntimeException("boom"));

        OpenAiEnrichmentProvider provider = provider(List.of("deepseek-chat", "deepseek-reasoner"));

        assertThatThrownBy(() -> enrichSample(provider))
                .isInstanceOf(IllegalStateException.class);

        verify(client).complete(any(), argThat(forModel("deepseek-chat")));
        verify(client).complete(any(), argThat(forModel("deepseek-reasoner")));
    }

    @Test
    @DisplayName("TC-OAI-07: cooldownMinutes=0 disables cooldown — failed model retried on the very next call")
    void cooldownDisabledRetriesImmediately() {
        when(client.complete(any(), argThat(forModel("deepseek-chat"))))
                .thenThrow(new RuntimeException("rate limited"))
                .thenReturn(responseWith(VALID_JSON));
        when(client.complete(any(), argThat(forModel("deepseek-reasoner"))))
                .thenReturn(responseWith(VALID_JSON));

        OpenAiEnrichmentProvider provider = provider(List.of("deepseek-chat", "deepseek-reasoner"), 0);
        enrichSample(provider); // deepseek-chat throws, deepseek-reasoner serves
        enrichSample(provider); // deepseek-chat retried (no cooldown), now succeeds

        verify(client, times(2)).complete(any(), argThat(forModel("deepseek-chat")));
    }

    @Test
    @DisplayName("TC-OAI-08: unparseable/truncated content throws so the chain moves on")
    void unparseableContentThrows() {
        when(client.complete(any(), any()))
                .thenReturn(responseWith("{\"employmentType\": \"full-time\"")); // no closing brace

        assertThatThrownBy(() -> enrichSample(provider(List.of("deepseek-chat"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("TC-OAI-09: JSON extracted from prose-wrapped reply")
    void extractsJsonFromProse() {
        String content = "Sure, here is the JSON:\n" + VALID_JSON + "\nLet me know if you need anything else.";
        when(client.complete(any(), any())).thenReturn(responseWith(content));

        JobEnrichment result = enrichSample(provider(List.of("deepseek-chat")));

        assertThat(result).isNotNull();
        assertThat(result.careerLevel()).isEqualTo("senior");
        assertThat(result.city()).isEqualTo("London");
    }

    @Test
    @DisplayName("TC-OAI-10: name() returns the configured name")
    void nameReturnsConfiguredName() {
        OpenAiEnrichmentProvider provider = provider(List.of("deepseek-chat"));

        assertThat(provider.name()).isEqualTo("deepseek");
    }
}
