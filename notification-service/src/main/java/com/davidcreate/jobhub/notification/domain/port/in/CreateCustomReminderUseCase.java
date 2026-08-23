package com.davidcreate.jobhub.notification.domain.port.in;

import com.davidcreate.jobhub.notification.domain.model.CustomReminder;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderChannel;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderStage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CreateCustomReminderUseCase {

    CustomReminder create(UUID userId, UUID applicationId, String title, String note,
                           Instant triggerAtUtc, List<CustomReminderChannel> channels,
                           CustomReminderStage stage);
}
