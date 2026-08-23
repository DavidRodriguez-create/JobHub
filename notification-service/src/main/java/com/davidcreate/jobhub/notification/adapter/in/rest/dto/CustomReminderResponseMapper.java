package com.davidcreate.jobhub.notification.adapter.in.rest.dto;

import com.davidcreate.jobhub.notification.contract.model.CustomReminderChannel;
import com.davidcreate.jobhub.notification.contract.model.CustomReminderResponse;
import com.davidcreate.jobhub.notification.contract.model.CustomReminderStage;
import com.davidcreate.jobhub.notification.contract.model.CustomReminderStatus;
import com.davidcreate.jobhub.notification.domain.model.CustomReminder;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.ZoneOffset;
import java.util.List;

@ApplicationScoped
public class CustomReminderResponseMapper {

    public CustomReminderResponse toResponse(CustomReminder domain) {
        List<CustomReminderChannel> channels = domain.getChannels().stream()
                .map(c -> CustomReminderChannel.valueOf(c.name()))
                .toList();

        return new CustomReminderResponse()
                .id(domain.getId())
                .applicationId(domain.getApplicationId())
                .title(domain.getTitle())
                .note(domain.getNote())
                .triggerAtUtc(domain.getTriggerAtUtc().atOffset(ZoneOffset.UTC))
                .channels(channels)
                .stage(domain.getStage() != null ? CustomReminderStage.valueOf(domain.getStage().name()) : null)
                .status(CustomReminderStatus.valueOf(domain.getStatus().name()))
                .createdAt(domain.getCreatedAt() != null ? domain.getCreatedAt().atOffset(ZoneOffset.UTC) : null)
                .updatedAt(domain.getUpdatedAt() != null ? domain.getUpdatedAt().atOffset(ZoneOffset.UTC) : null);
    }
}
