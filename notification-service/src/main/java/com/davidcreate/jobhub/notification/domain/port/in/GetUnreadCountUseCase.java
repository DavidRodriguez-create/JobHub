package com.davidcreate.jobhub.notification.domain.port.in;

import java.util.UUID;

public interface GetUnreadCountUseCase {

    long getUnreadCount(UUID userId);
}
