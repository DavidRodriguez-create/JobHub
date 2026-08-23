package com.davidcreate.jobhub.application.application.port.out;

import com.davidcreate.jobhub.application.domain.entity.InterestProfile;

import java.util.UUID;

public interface InterestProfileRepository {

    /**
     * Aggregates the user's application history into an interest profile: top locations,
     * companies, and title keywords, ordered by frequency, each capped at 5 entries.
     * Returns a profile with empty lists if the user has no application history
     * (or does not exist) — never {@code null}.
     */
    InterestProfile findInterestProfile(UUID userId);
}
