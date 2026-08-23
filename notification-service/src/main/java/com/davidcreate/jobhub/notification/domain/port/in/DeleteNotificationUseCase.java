package com.davidcreate.jobhub.notification.domain.port.in;

import java.util.UUID;

public interface DeleteNotificationUseCase {

    void delete(UUID id, UUID userId);
}
