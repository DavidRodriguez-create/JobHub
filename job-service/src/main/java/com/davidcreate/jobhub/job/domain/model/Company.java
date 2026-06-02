package com.davidcreate.jobhub.job.domain.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Company {

    private final String name;
    private final String logoUrl;
}
