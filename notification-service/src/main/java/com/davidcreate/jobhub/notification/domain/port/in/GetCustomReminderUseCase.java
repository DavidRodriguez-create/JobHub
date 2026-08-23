package com.davidcreate.jobhub.notification.domain.port.in;

import com.davidcreate.jobhub.notification.domain.model.CustomReminder;

import java.util.UUID;

public interface GetCustomReminderUseCase {

    CustomReminder get(UUID userId, UUID reminderId);
}
