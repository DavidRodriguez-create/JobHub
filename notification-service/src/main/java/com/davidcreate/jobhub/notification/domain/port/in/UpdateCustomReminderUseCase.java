package com.davidcreate.jobhub.notification.domain.port.in;

import com.davidcreate.jobhub.notification.domain.model.CustomReminder;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderChannel;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderStage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UpdateCustomReminderUseCase {

    /**
     * Partial update of a not-yet-fired reminder. Title is NOT editable (story #207, req 4):
     * the stored title is always preserved from create time.
     */
    CustomReminder update(UUID userId, UUID reminderId, String note,
                          Instant triggerAtUtc, List<CustomReminderChannel> channels,
                          CustomReminderStage stage);
}
