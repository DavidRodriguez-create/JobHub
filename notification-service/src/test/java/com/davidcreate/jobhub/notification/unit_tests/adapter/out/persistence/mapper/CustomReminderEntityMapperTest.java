package com.davidcreate.jobhub.notification.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.notification.adapter.out.persistence.entity.CustomReminderEntity;
import com.davidcreate.jobhub.notification.adapter.out.persistence.mapper.CustomReminderEntityMapper;
import com.davidcreate.jobhub.notification.domain.model.CustomReminder;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderChannel;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomReminderEntityMapper Unit Tests")
class CustomReminderEntityMapperTest {

    private CustomReminderEntityMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new CustomReminderEntityMapper();
    }

    private CustomReminderEntity baseEntity() {
        CustomReminderEntity entity = new CustomReminderEntity();
        entity.id = UUID.randomUUID();
        entity.userId = UUID.randomUUID();
        entity.applicationId = UUID.randomUUID();
        entity.title = "Prep";
        entity.triggerAtUtc = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
        entity.status = "SCHEDULED";
        entity.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        entity.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        return entity;
    }

    // CR-U-090
    @Test
    @DisplayName("CR-U-090: comma-string IN_APP,EMAIL parses to Set{IN_APP, EMAIL}")
    void parsesBothChannels() {
        CustomReminderEntity entity = baseEntity();
        entity.channels = "IN_APP,EMAIL";

        CustomReminder domain = mapper.toDomain(entity);

        assertThat(domain.getChannels()).containsExactlyInAnyOrder(CustomReminderChannel.IN_APP, CustomReminderChannel.EMAIL);
    }

    // CR-U-091
    @Test
    @DisplayName("CR-U-091: single channel string parses to a single-element Set")
    void parsesSingleChannel() {
        CustomReminderEntity entity = baseEntity();
        entity.channels = "IN_APP";

        CustomReminder domain = mapper.toDomain(entity);

        assertThat(domain.getChannels()).containsExactly(CustomReminderChannel.IN_APP);
    }

    // CR-U-092
    @Test
    @DisplayName("CR-U-092: Set{IN_APP, EMAIL} serialises to a comma-string round trip")
    void serialisesBothChannels() {
        CustomReminder domain = CustomReminder.builder()
                .userId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .title("Prep")
                .triggerAtUtc(Instant.now().plusSeconds(3600))
                .channels(Set.of(CustomReminderChannel.IN_APP, CustomReminderChannel.EMAIL))
                .status(CustomReminderStatus.SCHEDULED)
                .build();

        CustomReminderEntity entity = mapper.toEntity(domain);

        assertThat(entity.channels).contains("IN_APP").contains("EMAIL");
    }

    // CR-U-093
    @Test
    @DisplayName("CR-U-093: stage null and non-null round-trip")
    void stageRoundTrip() {
        CustomReminderEntity entityWithNullStage = baseEntity();
        entityWithNullStage.channels = "IN_APP";
        entityWithNullStage.stage = null;

        CustomReminderEntity entityWithStage = baseEntity();
        entityWithStage.channels = "IN_APP";
        entityWithStage.stage = "SCREENING";

        assertThat(mapper.toDomain(entityWithNullStage).getStage()).isNull();
        assertThat(mapper.toDomain(entityWithStage).getStage().name()).isEqualTo("SCREENING");
    }

    // CR-U-094
    @Test
    @DisplayName("CR-U-094: status SCHEDULED parses to CustomReminderStatus.SCHEDULED")
    void statusParses() {
        CustomReminderEntity entity = baseEntity();
        entity.channels = "IN_APP";

        CustomReminder domain = mapper.toDomain(entity);

        assertThat(domain.getStatus()).isEqualTo(CustomReminderStatus.SCHEDULED);
    }
}
