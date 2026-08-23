package com.davidcreate.jobhub.job.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Full-replace admin edit of the seven enrichable company fields (story #430, ADR 0025 D4).
 * {@code null} on any field is an intentional CLEAR, never "leave unchanged": the service
 * performs no partial merge with the existing row.
 */
@Getter
@Builder
public class CompanyUpdateCommand {

    private final String website;
    private final String industry;
    private final String size;
    private final String headquarters;
    private final String description;
    private final List<String> tags;
    private final String logoUrl;
}
