package com.davidcreate.jobhub.notification.domain.port.in;

import java.util.UUID;

public interface MarkAllNotificationsReadUseCase {

    void markAllNotificationsRead(UUID userId);
}
