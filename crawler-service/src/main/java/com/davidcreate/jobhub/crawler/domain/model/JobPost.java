package com.davidcreate.jobhub.crawler.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.commons.codec.digest.DigestUtils;

@Getter
@Builder
public class JobPost {

    private final UUID id;
    private final UUID targetId;
    private final String title;
    private final String url;
    private final String description;
    private final String contentHash;
    private final String city;
    private final String country;

    /**
     * Openings beyond the primary (city/country above). Populated for Lever postings that list
     * several locations via {@code categories.allLocations[]} (ADR 0017, story #319); empty for
     * single-location postings and for sources that do not expose multiple openings per posting.
     */
    @Builder.Default
    private final List<JobPostLocation> additionalLocations = List.of();

    private final Integer compensationMin;
    private final Integer compensationMax;
    private final String employmentType;
    private final List<String> languages;
    private final List<String> requirements;

    @Builder.Default
    private final OffsetDateTime firstSeenAt = OffsetDateTime.now();
    @Builder.Default
    private OffsetDateTime lastSeenAt = OffsetDateTime.now();

    public void markSeenAgain() {
        this.lastSeenAt = OffsetDateTime.now();
    }

    /**
     * The full opening set for this post: the primary opening (derived from city/country)
     * first, followed by any additional openings. Empty when there is no location data at
     * all (BR-9). This is the single source of truth for "what are this post's openings",
     * both the persistence layer (writing the child rows) and any future response mapping
     * should read this, not city/country and additionalLocations separately.
     */
    public List<JobPostLocation> locations() {
        List<JobPostLocation> result = new ArrayList<>();
        if (city != null || country != null) {
            result.add(JobPostLocation.builder()
                    .country(country)
                    .city(city)
                    .primary(true)
                    .build());
        }
        if (additionalLocations != null) {
            result.addAll(additionalLocations);
        }
        return Collections.unmodifiableList(result);
    }

    public static String computeHash(String title, String company, String city, String description) {
        String raw = normalize(title) + "|"
                + normalize(company) + "|"
                + normalize(city) + "|"
                + normalize(description);

        return DigestUtils.sha256Hex(raw);
    }

    private static String normalize(String value) {
        if (value == null)
            return "";
        return value.toLowerCase().trim().replaceAll("\\s+", " ");
    }
}
