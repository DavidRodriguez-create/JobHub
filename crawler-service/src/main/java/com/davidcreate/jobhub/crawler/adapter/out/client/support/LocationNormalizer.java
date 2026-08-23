package com.davidcreate.jobhub.crawler.adapter.out.client.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure, framework-free location canonicalization core (story #408, ADR 0021). Turns a raw,
 * possibly messy source location string into an ordered list of {@link NormalizedLocation}
 * openings, primary first: country spelling/alias/ISO-code variants collapse to one
 * canonical country name, bare US-state codes/names resolve to {@code country="United
 * States"} with the state in {@code city}, qualifier/work-mode noise ({@code (hybrid)},
 * {@code - Remote}) is stripped without losing the real place it was attached to, and a
 * {@code ;}/{@code |} separated string becomes multiple openings.
 *
 * <p>The core rule (never null, never dropped): anything that cannot be confidently matched
 * to a country, a US state, or the {@code Remote} sentinel is preserved, title-cased, as a
 * city-only opening ({@code country=null}). No dictionary miss is ever silently discarded.
 *
 * <p>Reused by both the crawl write path (through the {@link LocationParser} facade) and
 * {@code LocationNormalizationBackfillRunner} via {@link #normalizePair(String, String)}, so
 * canonicalization stays byte-for-byte identical between new crawls and the one-shot backfill.
 */
public final class LocationNormalizer {

    private static final String UNITED_STATES = "United States";
    private static final String REMOTE = "Remote";

    private static final Set<String> NOISE_WORDS = Set.of(
            "hybrid", "remote", "on-site", "on site", "onsite", "pt", "multiple", "heartland");

    private static final Pattern FULL_WRAP = Pattern.compile("^\\((.*)\\)$", Pattern.DOTALL);

    private static final Pattern REMOTE_MARKER = Pattern.compile(
            "(?i)^remote(\\s*-\\s*\\S.*|\\s+in\\s+.+|\\s+within\\s+.+)?$");

    private static final Pattern TRAILING_WORK_MODE_SUFFIX = Pattern.compile(
            "(?i)^(.*?)\\s*(?:-|\\bor\\b)\\s*"
                    + "(hybrid|remote opportunity|remote|on-site|on site|onsite|pt|multiple|heartland)\\s*$");

    // Country dictionary: lookup key (lower-case, dots removed, whitespace collapsed) -> canonical
    // full name. Deliberately excludes any bare 2-letter code that collides with a US-state code
    // (CA/CO/IN/MA/MD, ADR 0021 section 5): the US-state reading wins for those by construction,
    // since they simply never appear as a key here.
    private static final Map<String, String> COUNTRY_DICTIONARY = Map.ofEntries(
            Map.entry("us", UNITED_STATES),
            Map.entry("usa", UNITED_STATES),
            Map.entry("united states of america", UNITED_STATES),
            Map.entry("united states", UNITED_STATES),
            Map.entry("uk", "United Kingdom"),
            Map.entry("united kingdom", "United Kingdom"),
            Map.entry("es", "Spain"),
            Map.entry("spain", "Spain"),
            Map.entry("fr", "France"),
            Map.entry("france", "France"),
            Map.entry("nl", "Netherlands"),
            Map.entry("netherlands", "Netherlands"),
            Map.entry("ch", "Switzerland"),
            Map.entry("switzerland", "Switzerland"),
            Map.entry("jpn", "Japan"),
            Map.entry("japan", "Japan"),
            Map.entry("ire", "Ireland"),
            Map.entry("ireland", "Ireland"),
            Map.entry("united arab emirates", "United Arab Emirates"),
            Map.entry("uae", "United Arab Emirates"),
            Map.entry("czechia", "Czech Republic"),
            Map.entry("czech republic", "Czech Republic"),
            Map.entry("canada", "Canada"),
            Map.entry("colombia", "Colombia"),
            Map.entry("india", "India"),
            Map.entry("morocco", "Morocco"),
            Map.entry("moldova", "Moldova"),
            Map.entry("germany", "Germany"),
            Map.entry("italy", "Italy")
    );

    // US-state dictionary, keyed by 2-letter code AND full name (both lower-cased). "dc"/"d.c."
    // (dot-stripped to "dc")/"washington dc" are special-cased distinctly from the plain
    // "washington" state-name key, though both resolve to the same city text.
    private static final Map<String, String> US_STATE_DICTIONARY = buildStateDictionary();

    private LocationNormalizer() {
    }

    /**
     * Full pipeline: one raw source string to an ordered opening list, primary first. Handles
     * {@code ;}/{@code |} multi-location split. Returns an empty list for null/blank input or
     * for input that reduces to nothing but delimiters (never fabricates an opening). If every
     * split segment reduces to pure qualifier noise, the ORIGINAL raw string is preserved as a
     * single city-only opening rather than losing it (ADR 0021 section 3).
     */
    public static List<NormalizedLocation> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> nonBlankSegments = new ArrayList<>();
        for (String segment : raw.split("[;|]", -1)) {
            String trimmed = segment.trim();
            if (!trimmed.isEmpty()) {
                nonBlankSegments.add(trimmed);
            }
        }
        if (nonBlankSegments.isEmpty()) {
            return List.of();
        }

        List<NormalizedLocation> openings = new ArrayList<>();
        for (String segment : nonBlankSegments) {
            NormalizedLocation opening = normalizeOpening(segment);
            if (opening != null) {
                openings.add(opening);
            }
        }
        if (openings.isEmpty()) {
            // Every non-blank segment was pure noise: preserve the whole original raw string
            // rather than returning nothing (AC-408-33), distinct from the pure-delimiter case
            // above (AC-408-42), which legitimately returns empty.
            return List.of(new NormalizedLocation(titleCase(collapseWhitespace(raw.trim())), null));
        }
        return List.copyOf(openings);
    }

    /**
     * Backfill entry point: re-normalize an already-split stored {@code city}/{@code country}
     * pair (as held by an existing {@code job_post} row). Both blank/null yields an empty list
     * (nothing to normalize, nothing invented). When a country side is present it is
     * canonicalized (country/US-state/preserve-raw); a given, non-blank city is always kept
     * as-is (title-cased), never overwritten by a US-state reading. When only a city value is
     * present (legacy rows written before this story, where a flat single token like {@code
     * "Ca"} or {@code "us"} landed in the city column), it is treated as the flat single token
     * and canonicalized the same way {@link #canonicalizeToken(String)} would.
     */
    public static List<NormalizedLocation> normalizePair(String city, String country) {
        boolean cityBlank = city == null || city.isBlank();
        boolean countryBlank = country == null || country.isBlank();
        if (cityBlank && countryBlank) {
            return List.of();
        }
        if (!countryBlank) {
            NormalizedLocation countrySide = canonicalizeToken(country);
            String resolvedCountry = countrySide.country() != null ? countrySide.country() : countrySide.city();
            String resolvedCity;
            if (!cityBlank) {
                resolvedCity = titleCase(cleanEdgePunctuation(city));
            } else if (countrySide.country() != null) {
                resolvedCity = countrySide.city();
            } else {
                resolvedCity = null;
            }
            return List.of(new NormalizedLocation(resolvedCity, resolvedCountry));
        }
        return List.of(canonicalizeToken(city));
    }

    /**
     * Single-token canonicalization core: trims/cleans the token, then matches it against the
     * country dictionary, then the US-state dictionary, then the {@code Remote} sentinel.
     * A token matching none of those is preserved, title-cased, never null and never dropped
     * (the core preserve-raw rule).
     */
    public static NormalizedLocation canonicalizeToken(String token) {
        if (token == null) {
            return new NormalizedLocation(null, null);
        }
        String cleaned = cleanEdgePunctuation(token);
        if (cleaned.isEmpty()) {
            return new NormalizedLocation(null, null);
        }
        if (isPureRemoteMarker(cleaned)) {
            return new NormalizedLocation(null, REMOTE);
        }
        String key = buildLookupKey(cleaned);
        String country = COUNTRY_DICTIONARY.get(key);
        if (country != null) {
            return new NormalizedLocation(null, country);
        }
        String state = US_STATE_DICTIONARY.get(key);
        if (state != null) {
            return new NormalizedLocation(state, UNITED_STATES);
        }
        return new NormalizedLocation(titleCase(cleaned), null);
    }

    // ─── One-opening pipeline (steps 3-7 for a single already-split segment) ──────────────────

    private static NormalizedLocation normalizeOpening(String rawSegment) {
        String s = rawSegment.trim();

        Matcher fullWrap = FULL_WRAP.matcher(s);
        if (fullWrap.matches()) {
            String inner = collapseWhitespace(fullWrap.group(1).trim());
            if (isNoiseWord(inner)) {
                return null; // whole opening is pure qualifier noise, e.g. a lone "(Hybrid)"
            }
            s = inner; // alias-parenthetical, e.g. "(u.s.)" -> fed back through the pipeline
        } else {
            s = stripAttachedParens(s);
        }
        s = cleanEdgePunctuation(s);
        if (s.isEmpty()) {
            return null;
        }

        if (!isPureRemoteMarker(s)) {
            s = cleanEdgePunctuation(stripTrailingWorkModeSuffix(s));
        }
        if (s.isEmpty()) {
            return null;
        }
        if (isPureRemoteMarker(s)) {
            return new NormalizedLocation(null, REMOTE);
        }

        String[] parts = s.split(",", -1);
        if (parts.length <= 1) {
            return canonicalizeToken(s);
        }
        if (parts.length == 2) {
            return classifyCityCountryPair(parts[0], parts[1]);
        }
        String cityJoined = String.join(", ", Arrays.copyOfRange(parts, 0, parts.length - 1));
        return classifyCityCountryPair(cityJoined, parts[parts.length - 1]);
    }

    private static NormalizedLocation classifyCityCountryPair(String cityRaw, String countryRaw) {
        String city = titleCase(cleanEdgePunctuation(cityRaw));
        NormalizedLocation countrySide = canonicalizeToken(countryRaw);
        String country = countrySide.country() != null ? countrySide.country() : countrySide.city();
        return new NormalizedLocation(city.isEmpty() ? null : city, country);
    }

    // ─── Small string helpers ───────────────────────────────────────────────────────────────────

    private static boolean isNoiseWord(String text) {
        return text != null && NOISE_WORDS.contains(text.toLowerCase());
    }

    private static boolean isPureRemoteMarker(String s) {
        return s != null && REMOTE_MARKER.matcher(s.trim()).matches();
    }

    private static String stripTrailingWorkModeSuffix(String s) {
        Matcher m = TRAILING_WORK_MODE_SUFFIX.matcher(s);
        return m.matches() ? m.group(1) : s;
    }

    private static String stripAttachedParens(String s) {
        return s.replaceAll("\\([^)]*\\)", " ");
    }

    private static String cleanEdgePunctuation(String s) {
        if (s == null) {
            return "";
        }
        String result = s.trim()
                .replaceAll("^[)\\-:.\\s]+", "")
                .replaceAll("[)\\-:.\\s]+$", "");
        return collapseWhitespace(result);
    }

    private static String collapseWhitespace(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String buildLookupKey(String cleaned) {
        return collapseWhitespace(cleaned.toLowerCase().replace(".", ""));
    }

    private static String titleCase(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        String[] words = trimmed.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(" ");
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1).toLowerCase());
            }
        }
        return result.toString();
    }

    private static Map<String, String> buildStateDictionary() {
        String[][] states = {
                {"al", "Alabama"}, {"ak", "Alaska"}, {"az", "Arizona"}, {"ar", "Arkansas"},
                {"ca", "California"}, {"co", "Colorado"}, {"ct", "Connecticut"}, {"de", "Delaware"},
                {"fl", "Florida"}, {"ga", "Georgia"}, {"hi", "Hawaii"}, {"id", "Idaho"},
                {"il", "Illinois"}, {"in", "Indiana"}, {"ia", "Iowa"}, {"ks", "Kansas"},
                {"ky", "Kentucky"}, {"la", "Louisiana"}, {"me", "Maine"}, {"md", "Maryland"},
                {"ma", "Massachusetts"}, {"mi", "Michigan"}, {"mn", "Minnesota"}, {"ms", "Mississippi"},
                {"mo", "Missouri"}, {"mt", "Montana"}, {"ne", "Nebraska"}, {"nv", "Nevada"},
                {"nh", "New Hampshire"}, {"nj", "New Jersey"}, {"nm", "New Mexico"}, {"ny", "New York"},
                {"nc", "North Carolina"}, {"nd", "North Dakota"}, {"oh", "Ohio"}, {"ok", "Oklahoma"},
                {"or", "Oregon"}, {"pa", "Pennsylvania"}, {"ri", "Rhode Island"}, {"sc", "South Carolina"},
                {"sd", "South Dakota"}, {"tn", "Tennessee"}, {"tx", "Texas"}, {"ut", "Utah"},
                {"vt", "Vermont"}, {"va", "Virginia"}, {"wa", "Washington"}, {"wv", "West Virginia"},
                {"wi", "Wisconsin"}, {"wy", "Wyoming"}
        };
        java.util.Map<String, String> dict = new java.util.HashMap<>();
        for (String[] state : states) {
            String code = state[0];
            String name = state[1];
            dict.put(code, name);
            dict.put(name.toLowerCase(), name);
        }
        // Washington DC: distinct from the "washington" (state) key, same resulting city text.
        dict.put("dc", "Washington");
        dict.put("washington dc", "Washington");
        return Map.copyOf(dict);
    }
}
