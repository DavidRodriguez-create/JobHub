package com.davidcreate.jobhub.notification.adapter.in.rest.dto;

import com.davidcreate.jobhub.notification.contract.model.NotificationPage;

import java.util.List;

public final class NotificationPageDto {

    private NotificationPageDto() {
    }

    public static NotificationPage from(com.davidcreate.jobhub.notification.domain.model.NotificationPage domain) {
        List<com.davidcreate.jobhub.notification.contract.model.NotificationResponse> content = domain.getContent()
                .stream()
                .map(NotificationResponseDto::from)
                .toList();

        return new NotificationPage()
                .content(content)
                .page(domain.getPage())
                .size(domain.getSize())
                .totalElements(domain.getTotalElements())
                .totalPages(domain.getTotalPages());
    }
}
