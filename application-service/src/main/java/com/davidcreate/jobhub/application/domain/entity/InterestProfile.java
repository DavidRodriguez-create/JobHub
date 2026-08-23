package com.davidcreate.jobhub.application.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class InterestProfile {

    private final UUID userId;
    private final List<String> locations;
    private final List<String> companies;
    private final List<String> keywords;
}
