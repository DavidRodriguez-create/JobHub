package com.davidcreate.jobhub.notification.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.notification.adapter.out.persistence.entity.DigestRunEntity;
import com.davidcreate.jobhub.notification.adapter.out.persistence.mapper.DigestRunMapper;
import com.davidcreate.jobhub.notification.domain.model.DigestRun;
import com.davidcreate.jobhub.notification.domain.model.DigestRunStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DigestRunMapper Unit Tests")
class DigestRunMapperTest {

    private DigestRunMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new DigestRunMapper();
    }

    // TC-24
    @Test
    @DisplayName("maps_sent_entity_to_domain")
    void mapsSentEntityToDomain() {
        DigestRunEntity entity = new DigestRunEntity();
        entity.id = UUID.randomUUID();
        entity.userId = UUID.randomUUID();
        entity.sentAt = Instant.now();
        entity.jobCount = 6;
        entity.status = "sent";
        entity.errorMessage = null;

        DigestRun domain = mapper.toDomain(entity);

        assertThat(domain.getId()).isEqualTo(entity.id);
        assertThat(domain.getUserId()).isEqualTo(entity.userId);
        assertThat(domain.getSentAt()).isEqualTo(entity.sentAt);
        assertThat(domain.getJobCount()).isEqualTo(6);
        assertThat(domain.getStatus()).isEqualTo(DigestRunStatus.SENT);
        assertThat(domain.getErrorMessage()).isNull();
    }

    // TC-25
    @Test
    @DisplayName("maps_failed_entity_to_domain_with_error_message")
    void mapsFailedEntityToDomainWithErrorMessage() {
        DigestRunEntity entity = new DigestRunEntity();
        entity.id = UUID.randomUUID();
        entity.userId = UUID.randomUUID();
        entity.sentAt = Instant.now();
        entity.jobCount = 0;
        entity.status = "failed";
        entity.errorMessage = "application-service timeout";

        DigestRun domain = mapper.toDomain(entity);

        assertThat(domain.getStatus()).isEqualTo(DigestRunStatus.FAILED);
        assertThat(domain.getErrorMessage()).isEqualTo("application-service timeout");
    }

    // TC-26
    @Test
    @DisplayName("maps_domain_to_entity_for_persistence")
    void mapsDomainToEntityForPersistence() {
        UUID userId = UUID.randomUUID();
        Instant sentAt = Instant.now();
        DigestRun domain = DigestRun.builder()
                .userId(userId)
                .sentAt(sentAt)
                .jobCount(10)
                .status(DigestRunStatus.SENT)
                .errorMessage(null)
                .build();

        DigestRunEntity entity = mapper.toEntity(domain);

        assertThat(entity.userId).isEqualTo(userId);
        assertThat(entity.sentAt).isEqualTo(sentAt);
        assertThat(entity.jobCount).isEqualTo(10);
        assertThat(entity.status).isEqualTo("sent");
        assertThat(entity.errorMessage).isNull();
    }

    // TC-26b
    @Test
    @DisplayName("maps_skipped_status_round_trip")
    void mapsSkippedStatusRoundTrip() {
        UUID userId = UUID.randomUUID();
        Instant sentAt = Instant.now();
        DigestRun domain = DigestRun.builder()
                .userId(userId)
                .sentAt(sentAt)
                .jobCount(0)
                .status(DigestRunStatus.SKIPPED)
                .build();

        DigestRunEntity entity = mapper.toEntity(domain);
        assertThat(entity.status).isEqualTo("skipped");

        DigestRun roundTrip = mapper.toDomain(entity);
        assertThat(roundTrip.getStatus()).isEqualTo(DigestRunStatus.SKIPPED);
    }
}
