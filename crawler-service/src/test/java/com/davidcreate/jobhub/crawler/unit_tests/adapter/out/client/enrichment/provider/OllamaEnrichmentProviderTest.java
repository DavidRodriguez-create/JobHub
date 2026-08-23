package com.davidcreate.jobhub.crawler.unit_tests.adapter.out.client.enrichment.provider;

import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.OllamaClient;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.config.ProviderConfig;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.config.ProviderType;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider.OllamaEnrichmentProvider;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.CurrencyConverter;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.EnrichmentPrompt;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.FxRateConfig;
import com.davidcreate.jobhub.crawler.domain.model.JobEnrichment;
import com.davidcreate.jobhub.ollama.contract.model.ChatMessage;
import com.davidcreate.jobhub.ollama.contract.model.ChatRequest;
import com.davidcreate.jobhub.ollama.contract.model.ChatResponse;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OllamaEnrichmentProvider Unit Tests")
class OllamaEnrichmentProviderTest {

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
    OllamaClient client;

    private CurrencyConverter converter;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<CurrencyConverter> c = CurrencyConverter.class.getDeclaredConstructor(FxRateConfig.class);
        c.setAccessible(true);
        converter = c.newInstance(RATES);
    }

    private static ProviderConfig configOf(List<String> models) {
        return new ProviderConfig() {
            public String name() { return "ollama"; }
            public ProviderType type() { return ProviderType.OLLAMA; }
            public String baseUrl() { return "http://localhost:11434"; }
            public String apiKey() { return ""; }
            public List<String> models() { return models; }
            public int cooldownMinutes() { return 0; }
            public int readTimeoutMs() { return 600000; }
            public boolean enabled() { return true; }
            public int unusableReplyStrikes() { return 3; }
        };
    }

    private OllamaEnrichmentProvider provider(List<String> models) {
        return new OllamaEnrichmentProvider(configOf(models), client, new ObjectMapper(), converter);
    }

    private static ChatResponse responseWith(String content) {
        return new ChatResponse()
                .model("llama3.2")
                .done(true)
                .doneReason("stop")
                .message(content == null ? null : new ChatMessage().role("assistant").content(content))
                .promptEvalCount(100)
                .evalCount(20)
                .totalDuration(1_000_000_000L);
    }

    private JobEnrichment enrichSample(OllamaEnrichmentProvider provider) {
        return provider.enrich("Engineer", "Builds things in London.", "London", "United Kingdom");
    }

    @Test
    @DisplayName("TC-OLL-01: successful call returns a parsed JobEnrichment")
    void successfulCallReturnsParsedEnrichment() {
        when(client.chat(any())).thenReturn(responseWith(VALID_JSON));

        JobEnrichment result = enrichSample(provider(List.of("llama3.2")));

        assertThat(result).isNotNull();
        assertThat(result.careerLevel()).isEqualTo("senior");
        assertThat(result.city()).isEqualTo("London");
        verify(client, times(1)).chat(any());
    }

    @Test
    @DisplayName("TC-OLL-02: request shape — model, stream=false, format=json, temperature=0, system+user messages")
    void requestShape() {
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        when(client.chat(captor.capture())).thenReturn(responseWith(VALID_JSON));

        enrichSample(provider(List.of("llama3.2")));

        ChatRequest req = captor.getValue();
        assertThat(req.getModel()).isEqualTo("llama3.2");
        assertThat(req.getStream()).isFalse();
        assertThat(req.getFormat()).isEqualTo("json");
        assertThat(req.getOptions().getTemperature()).isEqualTo(0.0);
        assertThat(req.getMessages()).hasSize(2);
        assertThat(req.getMessages().get(0).getRole()).isEqualTo("system");
        assertThat(req.getMessages().get(0).getContent()).contains(EnrichmentPrompt.SYSTEM_PROMPT);
        assertThat(req.getMessages().get(1).getRole()).isEqualTo("user");
        assertThat(req.getMessages().get(1).getContent()).contains("Engineer");
    }

    @Test
    @DisplayName("TC-OLL-03: empty/blank response content throws IllegalStateException")
    void emptyResponseContentThrows() {
        when(client.chat(any())).thenReturn(responseWith(""));

        assertThatThrownBy(() -> enrichSample(provider(List.of("llama3.2"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    @DisplayName("TC-OLL-04: non-JSON content throws IllegalStateException")
    void nonJsonContentThrows() {
        when(client.chat(any())).thenReturn(responseWith("not json at all"));

        assertThatThrownBy(() -> enrichSample(provider(List.of("llama3.2"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("TC-OLL-05: no cooldown / no model-chain behavior — repeated calls always hit the single configured model")
    void noCooldownRepeatedCallsHitSameModel() {
        when(client.chat(any()))
                .thenThrow(new RuntimeException("temporarily unavailable"))
                .thenReturn(responseWith(VALID_JSON));

        OllamaEnrichmentProvider provider = provider(List.of("llama3.2"));

        assertThatThrownBy(() -> enrichSample(provider)).isInstanceOf(RuntimeException.class);
        JobEnrichment result = enrichSample(provider);

        assertThat(result).isNotNull();
        verify(client, times(2)).chat(any());
    }

    @Test
    @DisplayName("TC-OLL-06: name() returns the configured name")
    void nameReturnsConfiguredName() {
        OllamaEnrichmentProvider provider = provider(List.of("llama3.2"));

        assertThat(provider.name()).isEqualTo("ollama");
    }

    @Test
    @DisplayName("TC-OLL-07: only the first model in models is used even if multiple are configured")
    void onlyFirstModelIsUsed() {
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        when(client.chat(captor.capture())).thenReturn(responseWith(VALID_JSON));

        enrichSample(provider(List.of("llama3.2", "mistral")));

        assertThat(captor.getValue().getModel()).isEqualTo("llama3.2");
        verify(client, times(1)).chat(any());
    }
}
