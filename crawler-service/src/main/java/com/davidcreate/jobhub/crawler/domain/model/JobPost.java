package com.davidcreate.jobhub.crawler.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
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
