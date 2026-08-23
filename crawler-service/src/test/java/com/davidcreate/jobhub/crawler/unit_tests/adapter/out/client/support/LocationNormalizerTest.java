package com.davidcreate.jobhub.crawler.unit_tests.adapter.out.client.support;

import com.davidcreate.jobhub.crawler.adapter.out.client.support.LocationNormalizer;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.NormalizedLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story #408 (ADR 0021): {@link LocationNormalizer} unit tests, mirroring
 * {@code docs/qa/408-location-normalization-test-cases.md} sections A-H.
 */
@DisplayName("LocationNormalizer Unit Tests")
class LocationNormalizerTest {

    private static NormalizedLocation flat(String token) {
        return LocationNormalizer.canonicalizeToken(token);
    }

    // ─── A. canonicalizeToken / flat single-token dictionary core ─────────────────────────────

    @ParameterizedTest
    @DisplayName("QAE-408-U-01: ISO-2 canonicalization, any casing (AC-408-01, AC-408-15)")
    @CsvSource({
            "us,,United States",
            "Us,,United States",
            "es,,Spain",
            "Es,,Spain",
            "fr,,France",
            "Fr,,France",
            "nl,,Netherlands",
            "Nl,,Netherlands",
            "ch,,Switzerland",
            "Ch,,Switzerland"
    })
    void u01_iso2Canonicalization(String input, String expectedCity, String expectedCountry) {
        NormalizedLocation result = flat(input);
        assertThat(result.city()).isEqualTo(expectedCity);
        assertThat(result.country()).isEqualTo(expectedCountry);
    }

    @ParameterizedTest
    @DisplayName("QAE-408-U-02: US/UK alias spellings collapse to one canonical name (AC-408-02/03/05)")
    @CsvSource({
            "Usa,,United States",
            "usa,,United States",
            "U.s.,,United States",
            "u.s.,,United States",
            "United States Of America,,United States",
            "Uk,,United Kingdom",
            "uk,,United Kingdom",
            "U.k.,,United Kingdom",
            "u.k.,,United Kingdom"
    })
    void u02_usUkAliasSpellings(String input, String expectedCity, String expectedCountry) {
        NormalizedLocation result = flat(input);
        assertThat(result.city()).isEqualTo(expectedCity);
        assertThat(result.country()).isEqualTo(expectedCountry);
    }

    @ParameterizedTest
    @DisplayName("QAE-408-U-03: ISO-3 codes and additional documented aliases (AC-408-04/08)")
    @CsvSource({
            "jpn,,Japan",
            "Jpn,,Japan",
            "ire,,Ireland",
            "united arab emirates,,United Arab Emirates",
            "uae,,United Arab Emirates",
            "czechia,,Czech Republic",
            "Czechia,,Czech Republic"
    })
    void u03_iso3AndAliases(String input, String expectedCity, String expectedCountry) {
        NormalizedLocation result = flat(input);
        assertThat(result.city()).isEqualTo(expectedCity);
        assertThat(result.country()).isEqualTo(expectedCountry);
    }

    @Test
    @DisplayName("QAE-408-U-04: already-canonical passthrough + unrecognized code preserved (AC-408-06/07)")
    void u04_passthroughAndUnrecognizedPreserved() {
        assertThat(flat("United States")).isEqualTo(new NormalizedLocation(null, "United States"));
        assertThat(flat("France")).isEqualTo(new NormalizedLocation(null, "France"));
        assertThat(flat("Xx")).isEqualTo(new NormalizedLocation("Xx", null));
    }

    @ParameterizedTest
    @DisplayName("QAE-408-U-05: bare US-state codes, full state names, Washington DC (AC-408-09/10/11)")
    @CsvSource({
            "Ny,New York,United States",
            "Tx,Texas,United States",
            "Wa,Washington,United States",
            "California,California,United States",
            "New York,New York,United States",
            "dc,Washington,United States",
            "d.c.,Washington,United States",
            "washington dc,Washington,United States"
    })
    void u05_usStatesAndDc(String input, String expectedCity, String expectedCountry) {
        NormalizedLocation result = flat(input);
        assertThat(result.city()).isEqualTo(expectedCity);
        assertThat(result.country()).isEqualTo(expectedCountry);
    }

    @ParameterizedTest
    @DisplayName("QAE-408-U-06: ambiguous-code ruling, US-state wins for CA/CO/IN/MA/MD (AC-408-12/13/14)")
    @CsvSource({
            "CA,California,United States",
            "Ca,California,United States",
            "ca,California,United States",
            "Co,Colorado,United States",
            "In,Indiana,United States",
            "Ma,Massachusetts,United States",
            "Md,Maryland,United States",
            "Canada,,Canada",
            "Colombia,,Colombia",
            "India,,India",
            "Morocco,,Morocco",
            "Moldova,,Moldova"
    })
    void u06_ambiguousCodeRuling(String input, String expectedCity, String expectedCountry) {
        NormalizedLocation result = flat(input);
        assertThat(result.city()).isEqualTo(expectedCity);
        assertThat(result.country()).isEqualTo(expectedCountry);
    }

    // ─── B. qualifier / suffix / edge-punctuation stripping ───────────────────────────────────

    @ParameterizedTest
    @DisplayName("QAE-408-U-07: parenthetical/trailing-suffix qualifiers stripped, place survives (AC-408-16/17/28)")
    @CsvSource({
            "'Germany (hybrid)',,Germany",
            "'United States (on-site)',,United States",
            "'Berlin (remote)',Berlin,",
            "'Berlin - Remote',Berlin,",
            "'Germany - Hybrid',,Germany"
    })
    void u07_qualifierStripping(String input, String expectedCity, String expectedCountry) {
        List<NormalizedLocation> result = LocationNormalizer.normalize(input);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).city()).isEqualTo(expectedCity);
        assertThat(result.get(0).country()).isEqualTo(expectedCountry);
    }

    @ParameterizedTest
    @DisplayName("QAE-408-U-08: stray edge punctuation cleaned without losing content (AC-408-18)")
    @CsvSource({
            "Usa-,,United States",
            "Ca),California,United States",
            "'United States):',,United States"
    })
    void u08_strayEdgePunctuation(String input, String expectedCity, String expectedCountry) {
        List<NormalizedLocation> result = LocationNormalizer.normalize(input);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).city()).isEqualTo(expectedCity);
        assertThat(result.get(0).country()).isEqualTo(expectedCountry);
    }

    @ParameterizedTest
    @DisplayName("QAE-408-U-09: alias parenthetical fed back through canonicalization, not discarded (AC-408-19)")
    @CsvSource({
            "(u.s.),,United States",
            "(usa),,United States"
    })
    void u09_aliasParenthetical(String input, String expectedCity, String expectedCountry) {
        List<NormalizedLocation> result = LocationNormalizer.normalize(input);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).city()).isEqualTo(expectedCity);
        assertThat(result.get(0).country()).isEqualTo(expectedCountry);
    }

    // ─── C. multi-location split (;/|) ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-408-U-10: semicolon/pipe split into ordered openings (AC-408-20/21/22/23/24/25/27)")
    void u10_multiLocationSplit() {
        assertThat(LocationNormalizer.normalize("Germany; France")).containsExactly(
                new NormalizedLocation(null, "Germany"),
                new NormalizedLocation(null, "France"));

        assertThat(LocationNormalizer.normalize("Tx | Remote")).containsExactly(
                new NormalizedLocation("Texas", "United States"),
                new NormalizedLocation(null, "Remote"));

        assertThat(LocationNormalizer.normalize("Netherlands; France; Italy")).containsExactly(
                new NormalizedLocation(null, "Netherlands"),
                new NormalizedLocation(null, "France"),
                new NormalizedLocation(null, "Italy"));

        assertThat(LocationNormalizer.normalize("Germany;;France")).containsExactly(
                new NormalizedLocation(null, "Germany"),
                new NormalizedLocation(null, "France"));

        assertThat(LocationNormalizer.normalize("Germany; ;France")).containsExactly(
                new NormalizedLocation(null, "Germany"),
                new NormalizedLocation(null, "France"));

        assertThat(LocationNormalizer.normalize("Barcelona, Spain")).containsExactly(
                new NormalizedLocation("Barcelona", "Spain"));

        assertThat(LocationNormalizer.normalize("Germany; Remote - Us")).containsExactly(
                new NormalizedLocation(null, "Germany"),
                new NormalizedLocation(null, "Remote"));
    }

    // ─── D. Remote sentinel ─────────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @DisplayName("QAE-408-U-11: value reducing to nothing but a remote marker resolves to the sentinel (AC-408-26)")
    @CsvSource({
            "Remote",
            "Remote - Us",
            "Remote In Canada",
            "Remote - Na"
    })
    void u11_remoteSentinel(String input) {
        List<NormalizedLocation> result = LocationNormalizer.normalize(input);
        assertThat(result).containsExactly(new NormalizedLocation(null, "Remote"));
    }

    // ─── E. THE CORE RULE: preserve-raw, never null, never dropped ────────────────────────────

    @ParameterizedTest
    @DisplayName("QAE-408-U-12: unmappable single-token values preserved, title-cased, never nulled (AC-408-29/30/32)")
    @CsvSource({
            "And Africa",
            "Atlantic Time Zones",
            "Multiple Locations",
            "Amsterdam",
            "Emea",
            "Amer",
            "Apac",
            "North America",
            "Pst Or Est"
    })
    void u12_preserveRawSingleToken(String input) {
        NormalizedLocation result = flat(input);
        assertThat(result.country()).isNull();
        assertThat(result.city()).isNotNull().isNotBlank();
        String normalizedInputWords = input.toLowerCase().replaceAll("\\s+", " ").trim();
        String normalizedOutputWords = result.city().toLowerCase().replaceAll("\\s+", " ").trim();
        assertThat(normalizedOutputWords).isEqualTo(normalizedInputWords);
    }

    @Test
    @DisplayName("QAE-408-U-13: unmappable right-hand token in a City, Country pair preserved (AC-408-31)")
    void u13_unmappableCountrySlotPreserved() {
        List<NormalizedLocation> result = LocationNormalizer.normalize("Springfield, Narnia");
        assertThat(result).containsExactly(new NormalizedLocation("Springfield", "Narnia"));
    }

    @Test
    @DisplayName("QAE-408-U-14: whole-input pure noise preserved as one opening; noise-only segment with a surviving sibling dropped (AC-408-33/34)")
    void u14_pureNoisePreservedVsDroppedWithSibling() {
        List<NormalizedLocation> soleNoise = LocationNormalizer.normalize("(Hybrid)");
        assertThat(soleNoise).hasSize(1);
        assertThat(soleNoise.get(0).city()).isNotNull().isNotBlank();

        List<NormalizedLocation> withSibling = LocationNormalizer.normalize("Germany; (Hybrid)");
        assertThat(withSibling).containsExactly(new NormalizedLocation(null, "Germany"));
    }

    @Test
    @DisplayName("QAE-408-U-15: already-well-formed City, Country input unaffected by preserve-raw (AC-408-35)")
    void u15_alreadyWellFormedPassthrough() {
        List<NormalizedLocation> result = LocationNormalizer.normalize("Barcelona, Spain");
        assertThat(result).containsExactly(new NormalizedLocation("Barcelona", "Spain"));
    }

    // ─── F. null / blank / pure-delimiter edges (normalize) ───────────────────────────────────

    @Test
    @DisplayName("QAE-408-U-16: null, blank, and lone-delimiter input never crash and never fabricate an opening (AC-408-39/40/42)")
    void u16_nullBlankDelimiterEdges() {
        assertThat(LocationNormalizer.normalize(null)).isEmpty();
        assertThat(LocationNormalizer.normalize("")).isEmpty();
        assertThat(LocationNormalizer.normalize("   ")).isEmpty();
        assertThat(LocationNormalizer.normalize(";")).isEmpty();
        assertThat(LocationNormalizer.normalize("|")).isEmpty();
        assertThat(LocationNormalizer.normalize(" ; ")).isEmpty();
    }

    // ─── G. normalizePair (backfill entry point) ───────────────────────────────────────────────

    @Test
    @DisplayName("QAE-408-U-17: normalizePair null/blank -> empty; already-normalized input is a fixed point (AC-408-41/45)")
    void u17_normalizePairEdgesAndFixedPoint() {
        assertThat(LocationNormalizer.normalizePair(null, null)).isEmpty();
        assertThat(LocationNormalizer.normalizePair("", "")).isEmpty();
        assertThat(LocationNormalizer.normalizePair("   ", null)).isEmpty();
        assertThat(LocationNormalizer.normalizePair("Barcelona", "Spain"))
                .containsExactly(new NormalizedLocation("Barcelona", "Spain"));
        assertThat(LocationNormalizer.normalizePair("California", "United States"))
                .containsExactly(new NormalizedLocation("California", "United States"));
    }

    // ─── H. LOW priority / optional: compound "City (alias)", no comma ────────────────────────

    @Test
    @DisplayName("QAE-408-U-18 [optional]: compound City (alias) never crashes and never yields a fully-null opening")
    void u18_compoundCityAliasSafeDefault() {
        List<NormalizedLocation> result = LocationNormalizer.normalize("New York (usa)");
        assertThat(result).isNotEmpty();
        NormalizedLocation opening = result.get(0);
        assertThat(opening.city() != null || opening.country() != null).isTrue();
    }
}
