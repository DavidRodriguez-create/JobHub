package com.davidcreate.jobhub.job.domain.model;

import lombok.Builder;
import lombok.Getter;

/**
 * One opening (country/city pair) of a {@link JobPost}. Either part may be null; the
 * special value {@code "Remote"} may live in either field, exactly like the parent
 * {@code job_post.city}/{@code country} columns (no separate remote flag, per ADR 0017).
 */
@Getter
@Builder
public class JobLocation {

    private final String country;
    private final String city;

    @Builder.Default
    private final boolean primary = false;

    public boolean isPrimary() {
        return primary;
    }

    /**
     * Composes this opening the same way {@link JobPost#location()} composes the
     * primary: {@code city + ", " + country}, or the non-blank part alone.
     */
    public String composed() {
        boolean cityBlank = city == null || city.isBlank();
        boolean countryBlank = country == null || country.isBlank();
        if (cityBlank) {
            return countryBlank ? null : country;
        }
        if (countryBlank) {
            return city;
        }
        return city + ", " + country;
    }
}
