package com.davidcreate.jobhub.notification.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.notification.adapter.out.persistence.entity.CustomReminderEntity;
import com.davidcreate.jobhub.notification.domain.model.CustomReminder;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderChannel;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderStage;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class CustomReminderEntityMapper {

    public CustomReminder toDomain(CustomReminderEntity entity) {
        return CustomReminder.builder()
                .id(entity.id)
                .userId(entity.userId)
                .applicationId(entity.applicationId)
                .title(entity.title)
                .note(entity.note)
                .triggerAtUtc(entity.triggerAtUtc.toInstant())
                .channels(parseChannels(entity.channels))
                .stage(entity.stage != null ? CustomReminderStage.valueOf(entity.stage) : null)
                .status(CustomReminderStatus.valueOf(entity.status))
                .channelsFired(parseChannels(entity.channelsFired))
                .firedAtUtc(entity.firedAtUtc != null ? entity.firedAtUtc.toInstant() : null)
                .createdAt(entity.createdAt != null ? entity.createdAt.toInstant() : null)
                .updatedAt(entity.updatedAt != null ? entity.updatedAt.toInstant() : null)
                .build();
    }

    public CustomReminderEntity toEntity(CustomReminder domain) {
        CustomReminderEntity entity = new CustomReminderEntity();
        entity.id = domain.getId();
        entity.userId = domain.getUserId();
        entity.applicationId = domain.getApplicationId();
        entity.title = domain.getTitle();
        entity.note = domain.getNote();
        if (domain.getTriggerAtUtc() != null) {
            entity.triggerAtUtc = domain.getTriggerAtUtc().atOffset(ZoneOffset.UTC);
        }
        entity.channels = joinChannels(domain.getChannels());
        entity.stage = domain.getStage() != null ? domain.getStage().name() : null;
        entity.status = domain.getStatus().name();
        entity.channelsFired = joinChannels(domain.getChannelsFired());
        if (domain.getFiredAtUtc() != null) {
            entity.firedAtUtc = domain.getFiredAtUtc().atOffset(ZoneOffset.UTC);
        }
        if (domain.getCreatedAt() != null) {
            entity.createdAt = domain.getCreatedAt().atOffset(ZoneOffset.UTC);
        }
        if (domain.getUpdatedAt() != null) {
            entity.updatedAt = domain.getUpdatedAt().atOffset(ZoneOffset.UTC);
        }
        return entity;
    }

    private Set<CustomReminderChannel> parseChannels(String raw) {
        if (raw == null || raw.isBlank()) {
            return EnumSet.noneOf(CustomReminderChannel.class);
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(CustomReminderChannel::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(CustomReminderChannel.class)));
    }

    private String joinChannels(Set<CustomReminderChannel> channels) {
        if (channels == null || channels.isEmpty()) {
            return "";
        }
        return channels.stream().map(Enum::name).collect(Collectors.joining(","));
    }
}
