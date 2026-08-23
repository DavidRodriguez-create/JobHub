package com.davidcreate.jobhub.notification.domain.port.in;

import java.util.UUID;

public interface MarkNotificationReadUseCase {

    void markNotificationRead(UUID userId, UUID notificationId);
}
