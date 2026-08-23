package com.davidcreate.jobhub.notification.domain.port.in;

import com.davidcreate.jobhub.notification.domain.model.NotificationPreferences;

import java.util.UUID;

public interface GetPreferencesUseCase {

    NotificationPreferences getPreferences(UUID userId);
}
