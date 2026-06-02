package com.davidcreate.jobhub.application.adapter.out.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JobPostRemoteResponse(
        UUID id,
        String title,
        String url,
        String description,
        String location) {
}
