package com.davidcreate.jobhub.job.domain.model;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Company identity slug (story #428, ADR 0023 D3): a pure function from a raw crawl-time
 * company name to a normalised identity key, so two employers arriving from two different
 * ATS sources collapse onto one {@code crawler.company} row if and only if they produce the
 * same slug.
 *
 * <p>Steps S0-S12 run in this exact order (load-bearing: see ADR 0023 D3 and the QAE's
 * ordering-proof cases). No CDI, no clock, no framework annotation: this is deliberately a
 * pure static method taking the raw name as its only input.
 */
public final class CompanySlug {

    private CompanySlug() {}

    // S2: a parenthesised/bracketed segment and its content, applied repeatedly (handles
    // multiple/nested occurrences one shell at a time).
    private static final Pattern BRACKET_SEGMENT = Pattern.compile("[(\\[{][^()\\[\\]{}]*[)\\]}]");

    // S4: '/' between exactly one letter on each side (slash legal forms, e.g. "A/S").
    private static final Pattern SLASH_SINGLE_LETTERS =
            Pattern.compile("(?<![A-Za-z])([A-Za-z])/([A-Za-z])(?![A-Za-z])");

    // S5: a trailing internet suffix directly following a word character, dropped once.
    private static final Pattern INTERNET_SUFFIX =
            Pattern.compile("(?<=\\w)\\.(com|io|ai|co|net|org)\\b", Pattern.CASE_INSENSITIVE);

    // S6: dot, comma, straight quote, curly quote (U+2019), backtick, acute accent (U+00B4)
    // and double quote - deleted with no separator inserted.
    private static final Pattern DELETE_CHARS = Pattern.compile("[.,'’`´\"]");

    // S1 (NFKC) runs before S6, and U+00B4 ACUTE ACCENT's own compatibility decomposition
    // is SPACE + COMBINING ACUTE ACCENT (U+0020 U+0301), not the bare combining mark - by
    // the time S6 runs, the literal U+00B4 is already gone and this is what survives it.
    // Deleted as a unit (no separator), same as the other S6 characters.
    private static final Pattern ACUTE_ACCENT_NFKC_ARTIFACT = Pattern.compile(" ́");

    // S7: non-spacing combining marks left behind by NFD decomposition.
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{Mn}+");

    // S7: characters whose diacritic does not decompose via NFD, mapped explicitly.
    private static final Map<Character, String> NON_DECOMPOSABLE = Map.ofEntries(
            Map.entry('ø', "o"), Map.entry('Ø', "o"),
            Map.entry('đ', "d"), Map.entry('Đ', "d"),
            Map.entry('ð', "d"), Map.entry('Ð', "d"),
            Map.entry('þ', "th"), Map.entry('Þ', "th"),
            Map.entry('ł', "l"), Map.entry('Ł', "l"),
            Map.entry('æ', "ae"), Map.entry('Æ', "ae"),
            Map.entry('œ', "oe"), Map.entry('Œ', "oe"),
            Map.entry('ß', "ss"),
            Map.entry('ı', "i"),
            Map.entry('ħ', "h"), Map.entry('Ħ', "h"),
            Map.entry('ŧ', "t"), Map.entry('Ŧ', "t"),
            Map.entry('ĸ', "k"));

    // S9: everything outside [a-z0-9] becomes a separator.
    private static final Pattern NON_ALNUM_RUN = Pattern.compile("[^a-z0-9]+");

    // S10: closed legal-form set (lowercase, post-S9).
    private static final Set<String> LEGAL_FORMS = Set.of(
            "ab", "ag", "aps", "as", "asa", "bhd", "bv", "cia", "co", "company", "corp",
            "corporation", "cv", "doo", "gk", "gmbh", "inc", "incorporated", "kft", "kg", "kk",
            "llc", "llp", "ltd", "ltda", "limited", "nv", "oo", "oy", "oyj", "plc", "pt", "pte",
            "pty", "sa", "sarl", "sas", "sau", "sdn", "se", "sl", "slu", "sp", "spa", "sprl",
            "srl", "ug", "z", "zrt");

    // S10: connector words, stripped from the trailing position under the same rule.
    private static final Set<String> CONNECTORS = Set.of("and", "und", "y", "et", "e");

    // S11: truncation boundary.
    private static final int MAX_LENGTH = 120;

    // S12: final validation.
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    public static Optional<String> of(String rawName) {
        if (rawName == null) {
            return Optional.empty();
        }

        String s = rawName;
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);            // S1
        s = removeBracketedSegments(s);                                // S2
        s = s.replace("&", " and ");                                    // S3
        s = SLASH_SINGLE_LETTERS.matcher(s).replaceAll("$1$2");          // S4
        s = INTERNET_SUFFIX.matcher(s).replaceFirst("");                  // S5
        s = ACUTE_ACCENT_NFKC_ARTIFACT.matcher(s).replaceAll("");            // S6
        s = DELETE_CHARS.matcher(s).replaceAll("");                        // S6
        s = stripDiacritics(s);                                              // S7
        s = s.toLowerCase(Locale.ROOT);                                       // S8
        s = tokenize(s);                                                       // S9
        s = stripTrailingLegalTokens(s);                                        // S10
        s = truncateAtTokenBoundary(s, MAX_LENGTH);                              // S11

        if (s.isEmpty() || !SLUG_PATTERN.matcher(s).matches()) {                  // S12
            return Optional.empty();
        }
        return Optional.of(s);
    }

    private static String removeBracketedSegments(String s) {
        String previous;
        do {
            previous = s;
            s = BRACKET_SEGMENT.matcher(s).replaceAll("");
        } while (!s.equals(previous));
        return s;
    }

    private static String stripDiacritics(String s) {
        StringBuilder mapped = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String replacement = NON_DECOMPOSABLE.get(c);
            mapped.append(replacement != null ? replacement : c);
        }
        String decomposed = Normalizer.normalize(mapped.toString(), Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("");
    }

    private static String tokenize(String s) {
        String separated = NON_ALNUM_RUN.matcher(s).replaceAll("-");
        int start = 0;
        int end = separated.length();
        while (start < end && separated.charAt(start) == '-') {
            start++;
        }
        while (end > start && separated.charAt(end - 1) == '-') {
            end--;
        }
        return separated.substring(start, end);
    }

    private static String stripTrailingLegalTokens(String s) {
        if (s.isEmpty()) {
            return s;
        }
        List<String> tokens = new ArrayList<>(List.of(s.split("-")));
        while (tokens.size() > 1) {
            String last = tokens.get(tokens.size() - 1);
            if (LEGAL_FORMS.contains(last) || CONNECTORS.contains(last)) {
                tokens.remove(tokens.size() - 1);
            } else {
                break;
            }
        }
        return String.join("-", tokens);
    }

    private static String truncateAtTokenBoundary(String s, int maxLength) {
        if (s.length() <= maxLength) {
            return s;
        }
        String[] tokens = s.split("-");
        StringBuilder result = new StringBuilder();
        for (String token : tokens) {
            int candidateLength = result.length() == 0
                    ? token.length()
                    : result.length() + 1 + token.length();
            if (candidateLength > maxLength) {
                break;
            }
            if (result.length() > 0) {
                result.append('-');
            }
            result.append(token);
        }
        return result.toString();
    }
}
