package com.davidcreate.jobhub.notification.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.notification.adapter.out.persistence.entity.InterviewReminderSentEntity;
import com.davidcreate.jobhub.notification.adapter.out.persistence.mapper.InterviewReminderSentMapper;
import com.davidcreate.jobhub.notification.domain.model.InterviewReminderSent;
import com.davidcreate.jobhub.notification.domain.model.ReminderOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InterviewReminderSentMapper Unit Tests")
class InterviewReminderSentMapperTest {

    private final InterviewReminderSentMapper mapper = new InterviewReminderSentMapper();

    // TC-137
    @Test
    @DisplayName("maps_entity_to_domain")
    void mapsEntityToDomain() {
        InterviewReminderSentEntity entity = new InterviewReminderSentEntity();
        entity.id = UUID.randomUUID();
        entity.userId = UUID.randomUUID();
        entity.applicationId = UUID.randomUUID();
        entity.reminderOffset = "H24";
        entity.nextStepDate = LocalDate.of(2026, 6, 17);
        entity.channels = "in_app,email";
        entity.sentAt = Instant.now();

        InterviewReminderSent domain = mapper.toDomain(entity);

        assertThat(domain.getId()).isEqualTo(entity.id);
        assertThat(domain.getUserId()).isEqualTo(entity.userId);
        assertThat(domain.getApplicationId()).isEqualTo(entity.applicationId);
        assertThat(domain.getReminderOffset()).isEqualTo(ReminderOffset.H24);
        assertThat(domain.getNextStepDate()).isEqualTo(entity.nextStepDate);
        assertThat(domain.getChannels()).isEqualTo(entity.channels);
        assertThat(domain.getSentAt()).isEqualTo(entity.sentAt);
    }

    // TC-138
    @Test
    @DisplayName("maps_domain_to_entity_for_persistence")
    void mapsDomainToEntityForPersistence() {
        UUID userId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        Instant sentAt = Instant.now();
        InterviewReminderSent domain = InterviewReminderSent.builder()
                .userId(userId)
                .applicationId(applicationId)
                .reminderOffset(ReminderOffset.H1)
                .nextStepDate(LocalDate.of(2026, 6, 22))
                .channels("in_app")
                .sentAt(sentAt)
                .build();

        InterviewReminderSentEntity entity = mapper.toEntity(domain);

        assertThat(entity.userId).isEqualTo(userId);
        assertThat(entity.applicationId).isEqualTo(applicationId);
        assertThat(entity.reminderOffset).isEqualTo("H1");
        assertThat(entity.nextStepDate).isEqualTo(LocalDate.of(2026, 6, 22));
        assertThat(entity.channels).isEqualTo("in_app");
        assertThat(entity.sentAt).isEqualTo(sentAt);
    }

    // TC-139
    @Test
    @DisplayName("maps_both_reminder_offset_enum_values_round_trip")
    void mapsBothReminderOffsetEnumValuesRoundTrip() {
        InterviewReminderSent h24 = InterviewReminderSent.builder()
                .userId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .reminderOffset(ReminderOffset.H24)
                .nextStepDate(LocalDate.now())
                .channels("in_app")
                .sentAt(Instant.now())
                .build();
        InterviewReminderSent h1 = InterviewReminderSent.builder()
                .userId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .reminderOffset(ReminderOffset.H1)
                .nextStepDate(LocalDate.now())
                .channels("in_app")
                .sentAt(Instant.now())
                .build();

        assertThat(mapper.toDomain(mapper.toEntity(h24)).getReminderOffset()).isEqualTo(ReminderOffset.H24);
        assertThat(mapper.toDomain(mapper.toEntity(h1)).getReminderOffset()).isEqualTo(ReminderOffset.H1);
    }
}
