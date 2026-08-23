package com.davidcreate.jobhub.notification.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class NotificationPage {

    private final List<Notification> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
}
