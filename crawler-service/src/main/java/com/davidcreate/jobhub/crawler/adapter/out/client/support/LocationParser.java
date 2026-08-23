package com.davidcreate.jobhub.crawler.adapter.out.client.support;

import java.util.List;

/**
 * Thin facade over {@link LocationNormalizer} (story #408, ADR 0021), kept so the existing
 * single-opening {@code parseCity}/{@code parseCountry} call sites in {@code
 * LeverJobSourceClient}/{@code GreenhouseJobSourceClient} keep compiling and behaving, now with
 * canonicalization for free: country spelling/ISO-code/alias variants collapse to one name,
 * bare US-state codes resolve to {@code country="United States"} with the state in {@code
 * city}, qualifier noise is stripped, and any value that cannot be confidently classified is
 * preserved rather than nulled (ADR 0021 section 3).
 *
 * <p>Originally Greenhouse-only; promoted here so Lever's primary and additional-locations
 * mapping split identically (story #319, ADR 0017).
 */
public final class LocationParser {

    private LocationParser() {
    }

    public static String parseCity(String location) {
        List<NormalizedLocation> result = LocationNormalizer.normalize(location);
        return result.isEmpty() ? null : result.get(0).city();
    }

    public static String parseCountry(String location) {
        List<NormalizedLocation> result = LocationNormalizer.normalize(location);
        return result.isEmpty() ? null : result.get(0).country();
    }
}
