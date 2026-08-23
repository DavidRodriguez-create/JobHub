package com.davidcreate.jobhub.notification.domain.port.in;

import com.davidcreate.jobhub.notification.domain.model.NotificationPage;
import com.davidcreate.jobhub.notification.domain.model.ReadStatusFilter;

import java.util.UUID;

public interface ListNotificationsUseCase {

    NotificationPage listNotifications(UUID userId, int page, int size, ReadStatusFilter readStatus);
}
