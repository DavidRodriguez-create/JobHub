package com.davidcreate.jobhub.crawler.unit_tests.adapter.out.client.support;

import com.davidcreate.jobhub.crawler.adapter.out.client.support.CurrencyConverter;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.EnrichmentParser;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.FxRateConfig;
import com.davidcreate.jobhub.crawler.domain.model.JobEnrichment;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EnrichmentParser Unit Tests")
class EnrichmentParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    private CurrencyConverter converter;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<CurrencyConverter> c = CurrencyConverter.class.getDeclaredConstructor(FxRateConfig.class);
        c.setAccessible(true);
        converter = c.newInstance(RATES);
    }

    private JobEnrichment parse(String json) throws Exception {
        return EnrichmentParser.parse(MAPPER.readTree(json), converter);
    }

    // ─── Pre-existing tests (preserved) ───────────────────────────────────────

    @Test
    @DisplayName("maps valid fields and converts a stated GBP salary to EUR")
    void mapsValidFields() throws Exception {
        JobEnrichment e = parse("""
            {
              "employmentType": "full-time",
              "careerLevel": "senior",
              "languages": ["English"],
              "requirements": ["Go", "Distributed systems"],
              "city": "London",
              "country": "United Kingdom",
              "remote": false,
              "compensationMin": 115000,
              "compensationMax": 150000,
              "currency": "GBP"
            }""");

        assertThat(e.employmentType()).isEqualTo("full-time");
        assertThat(e.careerLevel()).isEqualTo("senior");
        assertThat(e.languages()).containsExactly("English");
        assertThat(e.requirements()).containsExactly("Go", "Distributed systems");
        assertThat(e.city()).isEqualTo("London");
        assertThat(e.country()).isEqualTo("United Kingdom");
        assertThat(e.compensationMinEur()).isEqualTo(134_550);
        assertThat(e.compensationMaxEur()).isEqualTo(175_500);
    }

    @Test
    @DisplayName("rejects off-list enum values, keeping them null")
    void rejectsBadEnums() throws Exception {
        JobEnrichment e = parse("""
            {"employmentType":"permanent","careerLevel":"staff","languages":[],"requirements":[]}""");

        assertThat(e.employmentType()).isNull();
        assertThat(e.careerLevel()).isNull();
        assertThat(e.languages()).isNull();
        assertThat(e.requirements()).isNull();
    }

    @Test
    @DisplayName("maps remote with no city to the Remote marker")
    void remoteMarker() throws Exception {
        JobEnrichment e = parse("""
            {"remote": true, "city": null, "country": "Spain"}""");

        assertThat(e.city()).isEqualTo("Remote");
        assertThat(e.country()).isEqualTo("Spain");
    }

    @Test
    @DisplayName("leaves compensation null when no figure or currency is present")
    void noCompensation() throws Exception {
        JobEnrichment e = parse("""
            {"compensationMin": null, "currency": null, "city": "Paris"}""");

        assertThat(e.compensationMinEur()).isNull();
        assertThat(e.compensationMaxEur()).isNull();
        assertThat(e.city()).isEqualTo("Paris");
    }

    @Test
    @DisplayName("single salary value sets min == max")
    void singleSalary() throws Exception {
        JobEnrichment e = parse("""
            {"compensationMin": 80000, "compensationMax": null, "currency": "EUR"}""");

        assertThat(e.compensationMinEur()).isEqualTo(80_000);
        assertThat(e.compensationMaxEur()).isEqualTo(80_000);
    }

    @Test
    @DisplayName("tolerates unknown extra keys and missing fields")
    void toleratesNoise() throws Exception {
        JobEnrichment e = parse("""
            {"careerLevel":"MID","somethingElse":42}""");

        assertThat(e.careerLevel()).isEqualTo("mid");
        assertThat(e.employmentType()).isNull();
        assertThat(e.city()).isNull();
    }

    // ─── EP-L-01..16: normalizeLanguages cases ────────────────────────────────

    @Test
    @DisplayName("EP-L-01: canonical names pass through unchanged")
    void epL01_canonicalPassThrough() {
        List<String> result = EnrichmentParser.normalizeLanguages(
                List.of("English", "Spanish", "French", "Chinese", "German"));
        assertThat(result).containsExactly("English", "Spanish", "French", "Chinese", "German");
    }

    @Test
    @DisplayName("EP-L-02: normalization is case-insensitive")
    void epL02_caseInsensitive() {
        List<String> result = EnrichmentParser.normalizeLanguages(
                List.of("ENGLISH", "spanish", "fReNcH"));
        assertThat(result).containsExactly("English", "Spanish", "French");
    }

    @Test
    @DisplayName("EP-L-03: Spanish variants (Espanol, Castilian, Castellano, es) map to Spanish")
    void epL03_spanishVariants() {
        List<String> result = EnrichmentParser.normalizeLanguages(
                List.of("Espanol", "Castilian", "Castellano", "es"));
        assertThat(result).containsExactly("Spanish");
    }

    @Test
    @DisplayName("EP-L-04: Chinese variants (Mandarin, Mandarin Chinese, zh, zh-CN, zh-TW, Simplified Chinese, Traditional Chinese) map to Chinese")
    void epL04_chineseVariants() {
        List<String> result = EnrichmentParser.normalizeLanguages(
                List.of("Mandarin", "Mandarin Chinese", "zh", "zh-CN", "zh-TW",
                        "Simplified Chinese", "Traditional Chinese"));
        assertThat(result).containsExactly("Chinese");
    }

    @Test
    @DisplayName("EP-L-05: German variants (Deutsch, de) map to German")
    void epL05_germanVariants() {
        List<String> result = EnrichmentParser.normalizeLanguages(List.of("Deutsch", "de"));
        assertThat(result).containsExactly("German");
    }

    @Test
    @DisplayName("EP-L-06: English variants (en, eng) map to English")
    void epL06_englishVariants() {
        List<String> result = EnrichmentParser.normalizeLanguages(List.of("en", "eng"));
        assertThat(result).containsExactly("English");
    }

    @Test
    @DisplayName("EP-L-07: French variants (Francais, fr) map to French")
    void epL07_frenchVariants() {
        List<String> result = EnrichmentParser.normalizeLanguages(List.of("Francais", "fr"));
        assertThat(result).containsExactly("French");
    }

    @Test
    @DisplayName("EP-L-08: programming languages are dropped entirely")
    void epL08_programmingLanguagesDropped() {
        List<String> result = EnrichmentParser.normalizeLanguages(
                List.of("Python", "Java", "JavaScript", "TypeScript", "SQL", "Go", "Golang",
                        "C", "C++", "C#", "CSharp", "Rust", "Ruby", "PHP", "Swift", "Kotlin",
                        "Scala", "R", "MATLAB", "Bash", "Shell", "HTML", "CSS"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("EP-L-09: list with only programming languages returns null via parse()")
    void epL09_allProgrammingReturnsNull() throws Exception {
        JobEnrichment e = parse("""
            {"languages": ["Python", "Java", "JavaScript"]}""");
        assertThat(e.languages()).isNull();
    }

    @Test
    @DisplayName("EP-L-10: unknown human languages all map to Unknown; dedup collapses them to a single entry")
    void epL10_unknownHumanLanguage() {
        // Swahili, Tagalog, Hindi all resolve to "Unknown"; dedup (first-seen on canonical)
        // means only one "Unknown" survives.
        List<String> result = EnrichmentParser.normalizeLanguages(
                List.of("Swahili", "Tagalog", "Hindi"));
        assertThat(result).containsExactly("Unknown");
    }

    @Test
    @DisplayName("EP-L-11: mixed canonical, variant, programming, and unknown produces correct result")
    void epL11_mixed() {
        List<String> result = EnrichmentParser.normalizeLanguages(
                List.of("English", "Python", "Deutsch", "Swahili", "fr"));
        assertThat(result).containsExactly("English", "German", "Unknown", "French");
    }

    @Test
    @DisplayName("EP-L-12: canonical + variant of same language deduplicated (first-seen)")
    void epL12_canonicalVariantDedupe() {
        List<String> result = EnrichmentParser.normalizeLanguages(
                List.of("Spanish", "Espanol", "es", "English"));
        assertThat(result).containsExactly("Spanish", "English");
    }

    @Test
    @DisplayName("EP-L-13: null/missing/empty languages list returns null via parse()")
    void epL13_nullOrEmptyLanguagesNull() throws Exception {
        JobEnrichment eNull = parse("""
            {"languages": null}""");
        JobEnrichment eMissing = parse("""
            {"employmentType": "full-time"}""");
        JobEnrichment eEmpty = parse("""
            {"languages": []}""");

        assertThat(eNull.languages()).isNull();
        assertThat(eMissing.languages()).isNull();
        assertThat(eEmpty.languages()).isNull();
    }

    @Test
    @DisplayName("EP-L-14: MAX_LANGUAGES cap (5) applied after dedupe — 6th entry dropped")
    void epL14_maxLanguagesCap() {
        List<String> result = EnrichmentParser.normalizeLanguages(
                List.of("English", "Spanish", "French", "Chinese", "German", "Swahili"));
        assertThat(result).hasSize(5);
        assertThat(result).containsExactly("English", "Spanish", "French", "Chinese", "German");
    }

    @Test
    @DisplayName("EP-L-15: leading/trailing whitespace in input is trimmed before matching")
    void epL15_whitespaceTrimmed() {
        List<String> result = EnrichmentParser.normalizeLanguages(
                List.of("  English  ", " deutsch ", " PYTHON "));
        assertThat(result).containsExactly("English", "German");
    }

    @Test
    @DisplayName("EP-L-16: SCHEMA_INSTRUCTIONS names the five canonical languages and excludes programming")
    void epL16_schemaInstructionsContent() {
        String schema = com.davidcreate.jobhub.crawler.adapter.out.client.support.EnrichmentPrompt.SCHEMA_INSTRUCTIONS;
        assertThat(schema).containsIgnoringCase("English");
        assertThat(schema).containsIgnoringCase("Spanish");
        assertThat(schema).containsIgnoringCase("French");
        assertThat(schema).containsIgnoringCase("Chinese");
        assertThat(schema).containsIgnoringCase("German");
        assertThat(schema).containsIgnoringCase("programming");
    }
}
