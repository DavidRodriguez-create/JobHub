package com.davidcreate.jobhub.job.unit_tests.adapter.in.rest.dto;

import com.davidcreate.jobhub.job.adapter.in.rest.dto.TriggerResponseMapper;
import com.davidcreate.jobhub.job.contract.model.TriggerResponse;
import com.davidcreate.jobhub.job.domain.model.TriggerKind;
import com.davidcreate.jobhub.job.domain.model.TriggerRequest;
import com.davidcreate.jobhub.job.domain.model.TriggerStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TriggerResponseMapper Unit Tests")
class TriggerResponseMapperTest {

    @Test
    @DisplayName("J-U-05: maps TriggerRequest to TriggerResponse correctly")
    void mapsToResponse() {
        UUID id = UUID.randomUUID();
        OffsetDateTime requestedAt = OffsetDateTime.now();

        TriggerRequest domain = TriggerRequest.builder()
                .id(id)
                .kind(TriggerKind.CRAWL)
                .status(TriggerStatus.QUEUED)
                .requestedAt(requestedAt)
                .build();

        TriggerResponse response = TriggerResponseMapper.toResponse(domain);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getKind().toString()).isEqualTo("crawl");
        assertThat(response.getStatus().toString()).isEqualTo("queued");
        assertThat(response.getRequestedAt()).isEqualTo(requestedAt);
    }

    @ParameterizedTest(name = "maps TriggerStatus.{0} to its JSON value")
    @EnumSource(value = TriggerStatus.class, names = {"CANCEL_REQUESTED", "CANCELLED"})
    @DisplayName("Story #58 / ADR 0006: maps the new cancel statuses to their JSON values")
    void mapsCancelStatuses(TriggerStatus status) {
        TriggerRequest domain = TriggerRequest.builder()
                .id(UUID.randomUUID())
                .kind(TriggerKind.ENRICHMENT)
                .status(status)
                .requestedAt(OffsetDateTime.now())
                .build();

        TriggerResponse response = TriggerResponseMapper.toResponse(domain);

        assertThat(response.getStatus().toString()).isEqualTo(status.value());
    }
}
