package com.davidcreate.jobhub.notification.adapter.in.rest.dto;

import com.davidcreate.jobhub.notification.contract.model.NotificationCategory;
import com.davidcreate.jobhub.notification.contract.model.NotificationResponse;
import com.davidcreate.jobhub.notification.contract.model.NotificationType;
import com.davidcreate.jobhub.notification.domain.model.Notification;

import java.time.ZoneOffset;

public final class NotificationResponseDto {

    private NotificationResponseDto() {
    }

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse()
                .id(notification.getId())
                .type(NotificationType.valueOf(notification.getType().name()))
                .category(NotificationCategory.valueOf(notification.getType().category().name()))
                .title(notification.getTitle())
                .message(notification.getMessage())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt().atOffset(ZoneOffset.UTC))
                .applicationId(notification.getApplicationId())
                .company(notification.getCompany())
                .jobTitle(notification.getJobTitle())
                .companyLogoUrl(notification.getCompanyLogoUrl());
    }
}
