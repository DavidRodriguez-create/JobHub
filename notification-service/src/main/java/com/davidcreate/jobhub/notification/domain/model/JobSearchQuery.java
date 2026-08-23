package com.davidcreate.jobhub.notification.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class JobSearchQuery {

    private final String keyword;
    private final List<String> locations;
    private final String postedWithin;
    private final String sort;
    private final int size;
}
