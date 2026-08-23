package com.davidcreate.jobhub.application.adapter.out.persistence.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extracts the most frequent meaningful words from a list of job titles, for use in
 * {@code InterestProfileResponse.keywords} (ADR 0008). Tokens are lowercased, stripped of
 * punctuation, and common stop words are excluded before counting frequency.
 */
public final class KeywordExtractor {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-z0-9+#.]+");

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "or", "a", "an", "in", "at", "for", "of", "to", "with",
            "is", "are", "we", "you", "our", "on", "by", "as", "from", "your",
            "this", "that", "be", "will", "all", "new", "remote", "hybrid", "onsite");

    private KeywordExtractor() {
    }

    /**
     * Returns up to {@code limit} keywords across all titles, ordered by descending
     * frequency (ties broken by first-seen order). Returns an empty list if no titles
     * yield any non-stop-word tokens.
     */
    public static List<String> topKeywords(List<String> titles, int limit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String title : titles) {
            if (title == null) {
                continue;
            }
            for (String token : TOKEN_SPLIT.split(title.toLowerCase())) {
                if (token.isBlank() || STOP_WORDS.contains(token)) {
                    continue;
                }
                counts.merge(token, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
