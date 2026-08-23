package com.davidcreate.jobhub.notification.adapter.out.mail;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DigestJobView {

    private final String id;
    private final String title;
    private final String companyName;
    private final String location;
    private final String logoUrl;
}
