package com.davidcreate.jobhub.notification.domain.port.in;

import java.util.UUID;

public interface CancelCustomReminderUseCase {

    void cancel(UUID userId, UUID reminderId);
}
