package com.davidcreate.jobhub.crawler.adapter.out.client.support;

import java.util.List;

/**
 * Default values applied to JobPost rows when the upstream feed does not
 * carry an explicit value. These exist so downstream filters (e.g. the
 * job-service language filter) have something to match against until the
 * enrichment pass replaces them with extracted values.
 */
public final class JobFieldDefaults {

    public static final List<String> DEFAULT_LANGUAGES = List.of("English");

    private JobFieldDefaults() {}

    /**
     * Map the free-form employment-type strings used by external boards
     * (Lever "Full-time", Greenhouse "Internship", etc.) onto the
     * api-contracts enum values. Returns {@code null} when the value is
     * blank or doesn't match a known type.
     */
    public static String normalizeEmploymentType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase().replace('_', '-').replace(' ', '-');
        return switch (key) {
            case "full-time", "fulltime", "permanent" -> "full-time";
            case "part-time", "parttime" -> "part-time";
            case "contract", "contractor", "temporary", "temp" -> "contract";
            case "freelance", "freelancer" -> "freelance";
            case "internship", "intern", "stagiaire" -> "internship";
            default -> null;
        };
    }
}
