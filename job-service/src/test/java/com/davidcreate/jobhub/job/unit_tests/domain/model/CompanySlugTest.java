package com.davidcreate.jobhub.job.unit_tests.domain.model;

import com.davidcreate.jobhub.job.domain.model.CompanySlug;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story #428 (ADR 0023 D3) / QAE-428-SLUG-01..17. Each case is traced step by step
 * against the S0-S12 pipeline in the ADR before its expected value is asserted here - per
 * the QAE doc's own reminder, a slug case is never trusted from a similar-looking worked
 * example without tracing it (this is exactly what caught the QAE doc's own SLUG-05 row 2
 * correction, and two further corrections made here, documented at their call sites).
 */
@DisplayName("CompanySlug Unit Tests")
class CompanySlugTest {

    private static Optional<String> slug(String raw) {
        return CompanySlug.of(raw);
    }

    // ── QAE-428-SLUG-01: S0, null/blank/whitespace-only input produces no slug ────────

    @ParameterizedTest(name = "QAE-428-SLUG-01 #{index}: \"{0}\" -> no slug")
    @MethodSource("slug01Cases")
    void s0NullBlankWhitespaceOnlyProducesNoSlug(String input) {
        assertThat(slug(input)).isEmpty();
    }

    static Stream<Arguments> slug01Cases() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(""),
                Arguments.of("   "),
                Arguments.of("\t\n"),
                Arguments.of("  "));
    }

    // ── QAE-428-SLUG-02: S1, NFKC folds full-width/compatibility forms ────────────────

    @Test
    @DisplayName("QAE-428-SLUG-02: full-width Latin \"Ｓｔｒｉｐｅ\" NFKC-folds to \"stripe\"")
    void s1NfkcFoldsFullWidthForms() {
        assertThat(slug("Ｓｔｒｉｐｅ")).contains("stripe");
    }

    // ── QAE-428-SLUG-03: S2, bracketed/parenthesised segments removed entirely ────────

    @ParameterizedTest(name = "QAE-428-SLUG-03 #{index}: \"{0}\" -> \"{1}\"")
    @MethodSource("slug03Cases")
    void s2BracketedSegmentsRemovedEntirely(String input, String expected) {
        assertThat(slug(input)).contains(expected);
    }

    static Stream<Arguments> slug03Cases() {
        return Stream.of(
                Arguments.of("Block (Square)", "block"),
                Arguments.of("On (On Running)", "on"),
                Arguments.of("Acme (Old) (Legacy)", "acme"),
                // Corrected from the QAE doc's literal "[Acme] Corp" (which brackets the
                // COMPANY name, not the legal suffix, and traces to "corp" under the
                // guard-preserves-single-token rule - inconsistent with rows 1-3's own
                // established pattern of "content outside the brackets survives"). Swapping
                // which token is bracketed keeps the stated purpose (square brackets are
                // also removed) and the stated expected value ("acme") consistent.
                Arguments.of("Acme [Corp]", "acme"));
    }

    // ── QAE-428-SLUG-04: S3, '&' becomes " and " ───────────────────────────────────────

    @ParameterizedTest(name = "QAE-428-SLUG-04 #{index}: \"{0}\" -> \"{1}\"")
    @MethodSource("slug04Cases")
    void s3AmpersandBecomesAnd(String input, String expected) {
        assertThat(slug(input)).contains(expected);
    }

    static Stream<Arguments> slug04Cases() {
        return Stream.of(
                Arguments.of("H&M", "h-and-m"),
                Arguments.of("H and M", "h-and-m"));
    }

    // ── QAE-428-SLUG-05: S4, '/' between two single letters, ordering vs S9 ───────────

    @Test
    @DisplayName("QAE-428-SLUG-05 #1: \"Ørsted A/S\" -> \"orsted\" (S4 merges A/S before S9 tokenizes)")
    void s4SlashSingleLettersOrstedAS() {
        assertThat(slug("Ørsted A/S")).contains("orsted");
    }

    @Test
    @DisplayName("QAE-428-SLUG-05 #2 (QAE doc's own corrected expectation): "
            + "\"A/S Test\" -> \"as-test\" (S10 strips only the LAST token; \"as\" is first, not stripped)")
    void s4SlashSingleLettersLastTokenOnly() {
        assertThat(slug("A/S Test")).contains("as-test");
    }

    // ── QAE-428-SLUG-06: S5, trailing internet suffix dropped once, ordering vs S6 ────

    @ParameterizedTest(name = "QAE-428-SLUG-06 #{index}: \"{0}\" -> \"{1}\"")
    @MethodSource("slug06Cases")
    void s5InternetSuffixDroppedOnce(String input, String expected) {
        assertThat(slug(input)).contains(expected);
    }

    static Stream<Arguments> slug06Cases() {
        return Stream.of(
                Arguments.of("Booking.com B.V.", "booking"),
                Arguments.of("Acme.io", "acme"),
                Arguments.of("Acme.ai", "acme"),
                Arguments.of("Acme.co", "acme"),
                Arguments.of("Acme.net", "acme"),
                Arguments.of("Acme.org", "acme"));
    }

    @Test
    @DisplayName("QAE-428-SLUG-06 #7: \"Acme.com.br\" -> the suffix is dropped only ONCE, "
            + "no crash, non-empty result (exact tokenisation of the trailing .br is not specified)")
    void s5InternetSuffixDroppedOnceGuard() {
        Optional<String> result = slug("Acme.com.br");
        assertThat(result).isPresent();
        assertThat(result.get()).isNotBlank();
    }

    // ── QAE-428-SLUG-07: S6, delete dot/comma/quotes/backtick/acute accent, no separator ─

    @ParameterizedTest(name = "QAE-428-SLUG-07 #{index}: \"{0}\" -> \"{1}\"")
    @MethodSource("slug07Cases")
    void s6DeleteCharsNoSeparator(String input, String expected) {
        assertThat(slug(input)).contains(expected);
    }

    static Stream<Arguments> slug07Cases() {
        return Stream.of(
                Arguments.of("S.A.", "sa"),
                Arguments.of("O'Reilly", "oreilly"),
                Arguments.of("O’Reilly", "oreilly"),
                Arguments.of("O`Reilly", "oreilly"),
                Arguments.of("O´Reilly", "oreilly"),
                // Corrected from the QAE doc's literal "the-best-co": traced through S6
                // (quotes deleted) then S9 (tokenize) then S10 ("co" IS a legal-form token,
                // and 2 tokens ["the","best"] remain after stripping it, well above the
                // guard's "at least one token would remain" floor) - S10 legitimately
                // strips it, exactly as it strips "kg"/"co"/"and"/"gmbh" down to one
                // surviving token in the ADR's own "Muller GmbH and Co KG" -> "muller"
                // worked example. "the-best-co" surviving unstripped would be inconsistent
                // with that example.
                Arguments.of("The \"Best\" Co", "the-best"),
                Arguments.of("Grupo Planeta, S.L.", "grupo-planeta"));
    }

    // ── QAE-428-SLUG-08: S7, diacritic stripping ───────────────────────────────────────

    @ParameterizedTest(name = "QAE-428-SLUG-08 real-name #{index}: \"{0}\" -> \"{1}\"")
    @MethodSource("slug08RealNameCases")
    void s7DiacriticStrippingRealNames(String input, String expected) {
        assertThat(slug(input)).contains(expected);
    }

    static Stream<Arguments> slug08RealNameCases() {
        return Stream.of(
                Arguments.of("Nestlé S.A.", "nestle"),
                Arguments.of("NESTLE SA", "nestle"),
                Arguments.of("Ørsted A/S", "orsted"),
                Arguments.of("İŞBANK", "isbank"));
    }

    @ParameterizedTest(name = "QAE-428-SLUG-08 synthetic #{index}: \"{0}\" -> \"{1}\"")
    @MethodSource("slug08NonDecomposableCases")
    void s7DiacriticStrippingNonDecomposableMap(String input, String expected) {
        assertThat(slug(input)).contains(expected);
    }

    static Stream<Arguments> slug08NonDecomposableCases() {
        return Stream.of(
                Arguments.of("Đilas", "dilas"),          // Đilas, đ/Đ -> d
                Arguments.of("Guðmundsson", "gudmundsson"),    // ð -> d
                Arguments.of("Þór Inc", "thor"),               // þ -> th, inc stripped by S10
                Arguments.of("Łukasiewicz", "lukasiewicz"),    // ł -> l
                Arguments.of("Æon Labs", "aeon-labs"),         // æ -> ae, labs NOT stripped
                Arguments.of("Cœur", "coeur"),                 // œ -> oe
                Arguments.of("Straße GmbH", "strasse"),        // ß -> ss, gmbh stripped by S10
                Arguments.of("Kırmızı", "kirmizi"),            // standalone dotless i -> i
                Arguments.of("Ħamrun", "hamrun"),              // ħ -> h
                Arguments.of("Aĸpat", "akpat"));          // Aĸpat, ĸ -> k
    }

    // ── QAE-428-SLUG-09: S8, lowercase with Locale.ROOT (the Turkish-I trap) ──────────

    @Test
    @DisplayName("QAE-428-SLUG-09 #1: \"İŞBANK\" -> \"isbank\" (dotted capital İ lowers to plain i)")
    void s8TurkishITrapDottedCapitalI() {
        assertThat(slug("İŞBANK")).contains("isbank");
    }

    @Test
    @DisplayName("QAE-428-SLUG-09 #2: \"INDITEX\" -> \"inditex\" under Locale.ROOT even with "
            + "the JVM default locale forced to Turkish (no production seam - only the test "
            + "manipulates Locale.getDefault())")
    void s8PlainAsciiIStaysIUnderTurkishDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertThat(slug("INDITEX")).contains("inditex");
        } finally {
            Locale.setDefault(previous);
        }
    }

    // ── QAE-428-SLUG-10: S9, non [a-z0-9] becomes a separator; collapse; trim ─────────

    @Test
    @DisplayName("QAE-428-SLUG-10 #1: \"Grupo Planeta\" -> \"grupo-planeta\"")
    void s9BasicTokenization() {
        assertThat(slug("Grupo Planeta")).contains("grupo-planeta");
    }

    @Test
    @DisplayName("QAE-428-SLUG-10 #2: \"Acme   Corp\" (3 spaces) collapses to one separator, then S10 removes corp")
    void s9CollapsesMultipleSeparators() {
        assertThat(slug("Acme   Corp")).contains("acme");
    }

    @Test
    @DisplayName("QAE-428-SLUG-10 #3: \"-Acme-\" leading/trailing punctuation trimmed")
    void s9TrimsLeadingTrailingSeparators() {
        assertThat(slug("-Acme-")).contains("acme");
    }

    @Test
    @DisplayName("QAE-428-SLUG-10 #4: \"Acme日本\" trailing CJK dropped, not transliterated")
    void s9DropsNonLatinSurvivors() {
        assertThat(slug("Acme日本")).contains("acme");
    }

    // ── QAE-428-SLUG-11: S10, legal-form/connector stripping, guarded, repeated ───────

    @ParameterizedTest(name = "QAE-428-SLUG-11 #{index}: \"{0}\" -> \"{1}\"")
    @MethodSource("slug11Cases")
    void s10LegalFormAndConnectorStripping(String input, String expected) {
        assertThat(slug(input)).contains(expected);
    }

    static Stream<Arguments> slug11Cases() {
        return Stream.of(
                Arguments.of("Co", "co"),
                Arguments.of("Y", "y"),
                Arguments.of("Müller GmbH & Co. KG", "muller"),
                Arguments.of("Acme and", "acme"),
                Arguments.of("H&M", "h-and-m"),
                Arguments.of("Allegro Sp. z o.o.", "allegro"),
                Arguments.of("Zalando SE", "zalando"));
    }

    // ── QAE-428-SLUG-12: S11, truncate to 120 chars at a token boundary ───────────────

    @Test
    @DisplayName("QAE-428-SLUG-12 #1: truncation never cuts mid-token, result stays <= 120 chars")
    void s11TruncatesAtTokenBoundaryNeverMidToken() {
        StringBuilder name = new StringBuilder();
        while (name.length() <= 120) {
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append("Acme");
        }

        Optional<String> result = slug(name.toString());
        assertThat(result).isPresent();
        String slug = result.get();
        assertThat(slug.length()).isLessThanOrEqualTo(120);
        for (String token : slug.split("-")) {
            assertThat(token).isEqualTo("acme");
        }
    }

    @Test
    @DisplayName("QAE-428-SLUG-12 #2: a token straddling the 120-char boundary is dropped whole, not cut")
    void s11DropsWholeStraddlingToken() {
        // 116 'a' characters, a separator, then a 10-char token that would straddle 120.
        String longToken = "a".repeat(116);
        String straddlingToken = "bcdefghijk";
        String name = longToken + " " + straddlingToken;

        Optional<String> result = slug(name);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(longToken);
    }

    // ── QAE-428-SLUG-13: S12, final validation - no survivors means no slug ───────────

    @ParameterizedTest(name = "QAE-428-SLUG-13 #{index}: \"{0}\" -> no slug")
    @MethodSource("slug13Cases")
    void s12NoSurvivorsMeansNoSlug(String input) {
        assertThat(slug(input)).isEmpty();
    }

    static Stream<Arguments> slug13Cases() {
        return Stream.of(
                Arguments.of("楽天"),   // 楽天, CJK only
                Arguments.of("###"),
                Arguments.of("(Hybrid)"));
    }

    // ── QAE-428-SLUG-14: the ADR's full worked-examples table, one consolidated case ──

    @ParameterizedTest(name = "QAE-428-SLUG-14 #{index}: \"{0}\" -> \"{1}\"")
    @MethodSource("slug14Cases")
    @DisplayName("QAE-428-SLUG-14 (regression, lift-verbatim): ADR 0023 D3 worked examples")
    void slug14AdrWorkedExamples(String input, String expected) {
        if (expected == null) {
            assertThat(slug(input)).isEmpty();
        } else {
            assertThat(slug(input)).contains(expected);
        }
    }

    static Stream<Arguments> slug14Cases() {
        return Stream.of(
                Arguments.of("Stripe", "stripe"),
                Arguments.of(" STRIPE ", "stripe"),
                Arguments.of("Stripe, Inc.", "stripe"),
                Arguments.of("Nestlé S.A.", "nestle"),
                Arguments.of("NESTLE SA", "nestle"),
                Arguments.of("Nestle", "nestle"),
                Arguments.of("Block (Square)", "block"),
                Arguments.of("On (On Running)", "on"),
                Arguments.of("Booking.com B.V.", "booking"),
                Arguments.of("Ørsted A/S", "orsted"),
                Arguments.of("H&M", "h-and-m"),
                Arguments.of("H and M", "h-and-m"),
                Arguments.of("Zalando SE", "zalando"),
                Arguments.of("Grupo Planeta, S.L.", "grupo-planeta"),
                Arguments.of("Müller GmbH & Co. KG", "muller"),
                Arguments.of("Allegro Sp. z o.o.", "allegro"),
                Arguments.of("İŞBANK", "isbank"),
                Arguments.of("Co", "co"),
                Arguments.of("Amazon Web Services", "amazon-web-services"),
                Arguments.of("楽天", (String) null));
    }

    // ── QAE-428-SLUG-15: deliberate non-merge, Amazon Web Services vs Amazon ──────────

    @Test
    @DisplayName("QAE-428-SLUG-15 (AC-428-07): \"Amazon Web Services\" and \"Amazon\" produce different slugs")
    void s15DeliberateNonMergeAmazon() {
        assertThat(slug("Amazon Web Services")).contains("amazon-web-services");
        assertThat(slug("Amazon")).contains("amazon");
        assertThat(slug("Amazon Web Services")).isNotEqualTo(slug("Amazon"));
    }

    // ── QAE-428-SLUG-16: deliberate non-merge, group/holding/international/labs/... ──

    @ParameterizedTest(name = "QAE-428-SLUG-16 #{index}: \"{0}\" -> \"{1}\" (AC-428-08)")
    @MethodSource("slug16Cases")
    void s16DeliberateNonMergeQualifierWords(String input, String expected) {
        assertThat(slug(input)).contains(expected);
        assertThat(slug(input)).isNotEqualTo(slug("Acme"));
    }

    static Stream<Arguments> slug16Cases() {
        return Stream.of(
                Arguments.of("Acme Group", "acme-group"),
                Arguments.of("Acme Holding", "acme-holding"),
                Arguments.of("Acme International", "acme-international"),
                Arguments.of("Acme Labs", "acme-labs"),
                Arguments.of("Acme Technologies", "acme-technologies"),
                Arguments.of("Acme Solutions", "acme-solutions"));
    }

    // ── QAE-428-SLUG-17: documented residual risk - NOT a defect, do not "fix" ───────

    @Test
    @DisplayName("QAE-428-SLUG-17 (informational, documented trade-off, NOT a bug): "
            + "\"Software AG\" and \"Software\" slug identically")
    void s17DocumentedResidualRiskLegalSuffixMerge() {
        assertThat(slug("Software AG")).contains("software");
        assertThat(slug("Software")).contains("software");
        assertThat(slug("Software AG")).isEqualTo(slug("Software"));
    }
}
