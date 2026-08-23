package com.davidcreate.jobhub.notification.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.net.URI;
import java.util.UUID;

@Getter
@Builder
public class DigestJob {

    private final UUID id;
    private final String title;
    private final String companyName;
    private final String location;
    private final URI companyLogoUrl;
}
