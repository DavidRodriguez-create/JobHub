package com.davidcreate.jobhub.notification.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class InterestProfile {

    private final UUID userId;
    private final List<String> locations;
    private final List<String> companies;
    private final List<String> keywords;

    public boolean isEmpty() {
        return isEmpty(locations) && isEmpty(companies) && isEmpty(keywords);
    }

    private static boolean isEmpty(List<String> values) {
        return values == null || values.isEmpty();
    }
}
