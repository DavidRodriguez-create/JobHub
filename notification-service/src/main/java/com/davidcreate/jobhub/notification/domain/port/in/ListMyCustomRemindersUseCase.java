package com.davidcreate.jobhub.notification.domain.port.in;

import com.davidcreate.jobhub.notification.domain.model.CustomReminder;

import java.util.List;
import java.util.UUID;

public interface ListMyCustomRemindersUseCase {

    List<CustomReminder> list(UUID userId, boolean includeFired);
}
