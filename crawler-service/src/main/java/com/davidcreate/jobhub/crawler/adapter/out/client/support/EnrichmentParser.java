package com.davidcreate.jobhub.crawler.adapter.out.client.support;

import com.davidcreate.jobhub.crawler.domain.model.JobEnrichment;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps the model's raw JSON onto a validated {@link JobEnrichment}. Defensive on
 * purpose — a small model may emit extra keys, wrong types or off-list enum
 * values, so every field is read leniently and anything invalid becomes null
 * rather than propagating garbage into the database.
 */
public final class EnrichmentParser {

    private static final Set<String> EMPLOYMENT_TYPES =
            Set.of("full-time", "part-time", "contract", "freelance", "internship");
    private static final Set<String> CAREER_LEVELS =
            Set.of("internship", "junior", "mid", "senior", "lead", "principal", "manager", "director");

    static final int MAX_LANGUAGES = 5;
    private static final int MAX_REQUIREMENTS = 15;

    // Canonical human language names (title-case stored form).
    private static final String LANG_ENGLISH = "English";
    private static final String LANG_SPANISH = "Spanish";
    private static final String LANG_FRENCH  = "French";
    private static final String LANG_CHINESE = "Chinese";
    private static final String LANG_GERMAN  = "German";

    private static final Set<String> CANONICAL_LANGUAGES =
            Set.of(LANG_ENGLISH, LANG_SPANISH, LANG_FRENCH, LANG_CHINESE, LANG_GERMAN);

    // Programming / markup languages to drop (lower-cased for comparison).
    private static final Set<String> PROGRAMMING_LANGUAGES = Set.of(
            "python", "java", "javascript", "typescript", "js", "ts", "sql",
            "go", "golang", "c", "c++", "c#", "csharp", "rust", "ruby", "php",
            "swift", "kotlin", "scala", "r", "matlab", "bash", "shell", "html", "css"
    );

    // Variant -> canonical (variant keys lower-cased; values are the canonical constants above).
    private static final Map<String, String> LANGUAGE_VARIANTS = Map.ofEntries(
            // Spanish
            Map.entry("espanol",             LANG_SPANISH),
            Map.entry("español",             LANG_SPANISH),
            Map.entry("castilian",           LANG_SPANISH),
            Map.entry("castellano",          LANG_SPANISH),
            Map.entry("es",                  LANG_SPANISH),
            // French
            Map.entry("francais",            LANG_FRENCH),
            Map.entry("français",            LANG_FRENCH),
            Map.entry("fr",                  LANG_FRENCH),
            // Chinese
            Map.entry("mandarin",            LANG_CHINESE),
            Map.entry("mandarin chinese",    LANG_CHINESE),
            Map.entry("simplified chinese",  LANG_CHINESE),
            Map.entry("traditional chinese", LANG_CHINESE),
            Map.entry("zh",                  LANG_CHINESE),
            Map.entry("zh-cn",               LANG_CHINESE),
            Map.entry("zh-tw",               LANG_CHINESE),
            // German
            Map.entry("deutsch",             LANG_GERMAN),
            Map.entry("de",                  LANG_GERMAN),
            // English
            Map.entry("en",                  LANG_ENGLISH),
            Map.entry("eng",                 LANG_ENGLISH)
    );

    private EnrichmentParser() {}

    public static JobEnrichment parse(JsonNode node, CurrencyConverter converter) {
        String employmentType = enumValue(text(node, "employmentType"), EMPLOYMENT_TYPES);
        String careerLevel = enumValue(text(node, "careerLevel"), CAREER_LEVELS);
        List<String> rawLanguages = stringList(node.get("languages"), MAX_LANGUAGES * 4);
        List<String> languages = normalizeLanguages(rawLanguages);
        List<String> requirements = stringList(node.get("requirements"), MAX_REQUIREMENTS);

        String city = text(node, "city");
        String country = text(node, "country");
        if (node.path("remote").asBoolean(false) && (city == null || city.isBlank())) {
            city = "Remote";
        }

        Integer minEur = null;
        Integer maxEur = null;
        Integer min = intValue(node, "compensationMin");
        Integer max = intValue(node, "compensationMax");
        String currency = text(node, "currency");
        if (min != null && currency != null) {
            String code = currency.toUpperCase();
            minEur = converter.toEur(code, min);
            maxEur = max != null ? converter.toEur(code, max) : minEur;
        }

        return new JobEnrichment(
                employmentType,
                careerLevel,
                languages.isEmpty() ? null : languages,
                requirements.isEmpty() ? null : requirements,
                city,
                country,
                minEur,
                maxEur);
    }

    /**
     * Normalises a raw list of language strings from the LLM or from a stored DB row:
     * <ol>
     *   <li>lower + trim each entry</li>
     *   <li>drop if it is a programming / markup language</li>
     *   <li>map known variants to the canonical title-case name</li>
     *   <li>if a canonical name matches directly (case-insensitive), keep it</li>
     *   <li>otherwise emit "Unknown"</li>
     *   <li>deduplicate preserving first-seen order</li>
     *   <li>cap at {@link #MAX_LANGUAGES}</li>
     * </ol>
     * Returns an empty list (never null) — callers map empty to null as required.
     */
    public static List<String> normalizeLanguages(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String key = trimmed.toLowerCase();

            // Step 1: drop programming languages
            if (PROGRAMMING_LANGUAGES.contains(key)) {
                continue;
            }

            // Step 2: map variant to canonical
            String canonical = LANGUAGE_VARIANTS.get(key);
            if (canonical == null) {
                // Step 3: check if it directly matches a canonical (case-insensitive)
                canonical = CANONICAL_LANGUAGES.stream()
                        .filter(c -> c.equalsIgnoreCase(key))
                        .findFirst()
                        .orElse("Unknown");
            }

            // Step 4: dedupe (first-seen) then cap
            if (seen.add(canonical) && out.size() < MAX_LANGUAGES) {
                out.add(canonical);
            }
        }
        return out;
    }

    private static String enumValue(String raw, Set<String> allowed) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toLowerCase();
        return allowed.contains(key) ? key : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isValueNode()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static Integer intValue(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            return v.asInt();
        }
        String digits = v.asText().replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : Integer.valueOf(digits);
    }

    private static List<String> stringList(JsonNode array, int limit) {
        List<String> out = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return out;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode item : array) {
            if (!item.isValueNode()) {
                continue;
            }
            String s = item.asText().trim();
            if (!s.isEmpty() && seen.add(s.toLowerCase()) && out.size() < limit) {
                out.add(s);
            }
        }
        return out;
    }
}
