package com.davidcreate.jobhub.job.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
     * All openings for this posting, primary opening first (ADR 0017). Empty when the
     * posting has no location data at all. The mapper guarantees primary-first ordering,
     * not incidental JPA/DB ordering.
     */
    @Builder.Default
    private final List<JobLocation> locations = Collections.emptyList();

    private final OffsetDateTime firstSeenAt;
    private final OffsetDateTime lastSeenAt;

    private final Integer compensationMin;
    private final Integer compensationMax;
    private final EmploymentType employmentType;
    private final CareerLevel careerLevel;
    private final List<String> languages;
    private final List<String> requirements;

    private final Company company;
    private final String source;

    public String location() {
        if (city == null || city.isBlank()) {
            return country;
        }
        if (country == null || country.isBlank()) {
            return city;
        }
        return city + ", " + country;
    }
}
