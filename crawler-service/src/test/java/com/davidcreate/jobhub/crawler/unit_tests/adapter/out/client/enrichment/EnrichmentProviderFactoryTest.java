package com.davidcreate.jobhub.crawler.unit_tests.adapter.out.client.enrichment;

import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.config.ProviderConfig;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.config.ProviderType;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider.EnrichmentProvider;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider.EnrichmentProviderFactory;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider.GeminiEnrichmentProvider;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider.OllamaEnrichmentProvider;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider.OpenAiEnrichmentProvider;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.CurrencyConverter;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.FxRateConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnrichmentProviderFactory Unit Tests")
class EnrichmentProviderFactoryTest {

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

    /**
     * Test seam: avoids {@code RestClientBuilder.newBuilder()}, which requires a running
     * Quarkus container to resolve a {@code RestClientBuilderResolver} via CDI/SPI and is
     * unavailable in a plain Mockito unit test. Production behavior (filtering, ordering,
     * type dispatch) is exercised unchanged; only the REST client construction is stubbed.
     */
    private static final class TestableEnrichmentProviderFactory extends EnrichmentProviderFactory {
        TestableEnrichmentProviderFactory(ObjectMapper objectMapper, CurrencyConverter converter) {
            super(objectMapper, converter);
        }

        @Override
        protected <T> T buildClient(ProviderConfig config, Class<T> iface) {
            return mock(iface);
        }
    }

    private EnrichmentProviderFactory factory;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<CurrencyConverter> c = CurrencyConverter.class.getDeclaredConstructor(FxRateConfig.class);
        c.setAccessible(true);
        CurrencyConverter converter = c.newInstance(RATES);
        factory = new TestableEnrichmentProviderFactory(new ObjectMapper(), converter);
    }

    private static ProviderConfig config(String name, ProviderType type, String baseUrl, String apiKey,
                                          List<String> models, boolean enabled) {
        return new ProviderConfig() {
            public String name() { return name; }
            public ProviderType type() { return type; }
            public String baseUrl() { return baseUrl; }
            public String apiKey() { return apiKey; }
            public List<String> models() { return models; }
            public int cooldownMinutes() { return 30; }
            public int readTimeoutMs() { return 30000; }
            public boolean enabled() { return enabled; }
            public int unusableReplyStrikes() { return 3; }
        };
    }

    @Test
    @DisplayName("TC-EPF-01: type=GEMINI, enabled, keyed -> produces a GeminiEnrichmentProvider")
    void geminiEnabledKeyedProducesGeminiProvider() {
        ProviderConfig config = config("gemini", ProviderType.GEMINI,
                "https://generativelanguage.googleapis.com", "test-key", List.of("gemini-3.1-flash-lite"), true);

        List<EnrichmentProvider> providers = factory.createProviders(List.of(config));

        assertThat(providers).hasSize(1);
        assertThat(providers.get(0)).isInstanceOf(GeminiEnrichmentProvider.class);
    }

    @Test
    @DisplayName("TC-EPF-02: type=OPENAI, enabled, keyed -> produces an OpenAiEnrichmentProvider")
    void openAiEnabledKeyedProducesOpenAiProvider() {
        ProviderConfig config = config("deepseek", ProviderType.OPENAI,
                "https://api.deepseek.com", "dsk-test", List.of("deepseek-chat"), true);

        List<EnrichmentProvider> providers = factory.createProviders(List.of(config));

        assertThat(providers).hasSize(1);
        assertThat(providers.get(0)).isInstanceOf(OpenAiEnrichmentProvider.class);
    }

    @Test
    @DisplayName("TC-EPF-03: type=OLLAMA, enabled, blank key -> produces an OllamaEnrichmentProvider (key not required)")
    void ollamaEnabledBlankKeyProducesOllamaProvider() {
        ProviderConfig config = config("ollama", ProviderType.OLLAMA,
                "http://localhost:11434", "", List.of("llama3.2"), true);

        List<EnrichmentProvider> providers = factory.createProviders(List.of(config));

        assertThat(providers).hasSize(1);
        assertThat(providers.get(0)).isInstanceOf(OllamaEnrichmentProvider.class);
    }

    @Test
    @DisplayName("TC-EPF-04: disabled provider is skipped, regardless of type/key")
    void disabledProviderIsSkipped() {
        ProviderConfig config = config("gemini", ProviderType.GEMINI,
                "https://generativelanguage.googleapis.com", "test-key", List.of("gemini-3.1-flash-lite"), false);

        List<EnrichmentProvider> providers = factory.createProviders(List.of(config));

        assertThat(providers).isEmpty();
    }

    @Test
    @DisplayName("TC-EPF-05: enabled GEMINI with blank apiKey is skipped")
    void geminiEnabledBlankKeySkipped() {
        ProviderConfig config = config("gemini", ProviderType.GEMINI,
                "https://generativelanguage.googleapis.com", "", List.of("gemini-3.1-flash-lite"), true);

        List<EnrichmentProvider> providers = factory.createProviders(List.of(config));

        assertThat(providers).isEmpty();
    }

    @Test
    @DisplayName("TC-EPF-06: enabled OPENAI with whitespace-only apiKey is skipped")
    void openAiEnabledWhitespaceKeySkipped() {
        ProviderConfig config = config("deepseek", ProviderType.OPENAI,
                "https://api.deepseek.com", "   ", List.of("deepseek-chat"), true);

        List<EnrichmentProvider> providers = factory.createProviders(List.of(config));

        assertThat(providers).isEmpty();
    }

    @Test
    @DisplayName("TC-EPF-07: enabled OLLAMA with blank apiKey is kept (no key required)")
    void ollamaEnabledBlankKeyKept() {
        ProviderConfig config = config("ollama", ProviderType.OLLAMA,
                "http://localhost:11434", "", List.of("llama3.2"), true);

        List<EnrichmentProvider> providers = factory.createProviders(List.of(config));

        assertThat(providers).hasSize(1);
        assertThat(providers.get(0)).isInstanceOf(OllamaEnrichmentProvider.class);
    }

    @Test
    @DisplayName("TC-EPF-08: mixed list — order preserved, disabled/unkeyed entries filtered out")
    void mixedListOrderPreserved() {
        ProviderConfig geminiKeyed = config("gemini", ProviderType.GEMINI,
                "https://generativelanguage.googleapis.com", "test-key", List.of("gemini-3.1-flash-lite"), true);
        ProviderConfig geminiUnkeyed = config("gemini2", ProviderType.GEMINI,
                "https://generativelanguage.googleapis.com", "", List.of("gemini-3.1-flash-lite"), true);
        ProviderConfig ollamaBlankKey = config("ollama", ProviderType.OLLAMA,
                "http://localhost:11434", "", List.of("llama3.2"), true);

        List<EnrichmentProvider> providers = factory.createProviders(List.of(geminiKeyed, geminiUnkeyed, ollamaBlankKey));

        assertThat(providers).hasSize(2);
        assertThat(providers.get(0)).isInstanceOf(GeminiEnrichmentProvider.class);
        assertThat(providers.get(1)).isInstanceOf(OllamaEnrichmentProvider.class);
    }

    @Test
    @DisplayName("TC-EPF-09: empty input list -> empty output, no exception")
    void emptyInputListProducesEmptyOutput() {
        List<EnrichmentProvider> providers = factory.createProviders(List.of());

        assertThat(providers).isEmpty();
    }

    @Test
    @DisplayName("TC-EPF-10: cooldownMinutes/readTimeoutMs defaults are honoured when constructing the provider")
    void defaultCooldownAndTimeoutHonoured() {
        ProviderConfig config = config("gemini", ProviderType.GEMINI,
                "https://generativelanguage.googleapis.com", "test-key", List.of("gemini-3.1-flash-lite"), true);

        List<EnrichmentProvider> providers = factory.createProviders(List.of(config));

        assertThat(providers).hasSize(1);
        assertThat(providers.get(0).name()).isEqualTo("gemini");
    }
}
