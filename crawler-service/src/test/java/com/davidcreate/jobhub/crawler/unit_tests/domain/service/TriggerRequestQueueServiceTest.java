package com.davidcreate.jobhub.crawler.unit_tests.domain.service;

import com.davidcreate.jobhub.crawler.domain.exception.ConflictException;
import com.davidcreate.jobhub.crawler.domain.exception.ResourceNotFoundException;
import com.davidcreate.jobhub.crawler.domain.model.TriggerKind;
import com.davidcreate.jobhub.crawler.domain.model.TriggerOrigin;
import com.davidcreate.jobhub.crawler.domain.model.TriggerRequest;
import com.davidcreate.jobhub.crawler.domain.model.TriggerStatus;
import com.davidcreate.jobhub.crawler.domain.port.out.TriggerRequestQueue;
import com.davidcreate.jobhub.crawler.domain.service.TriggerRequestQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TriggerRequestQueueService (story #582)")
class TriggerRequestQueueServiceTest {

    @Mock
    TriggerRequestQueue triggerRequestQueue;

    TriggerRequestQueueService service;

    @BeforeEach
    void setUp() {
        service = new TriggerRequestQueueService(triggerRequestQueue);
    }

    // ── TR-04 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("TR-04: a running row of the kind exists, no queued row - queue same kind - "
            + "insert allowed, queued created, running row untouched")
    void queueSucceedsWhenOnlyARunningRowOfTheSameKindExists() {
        UUID requestedBy = UUID.randomUUID();
        TriggerRequest queuedRow = TriggerRequest.builder()
                .id(UUID.randomUUID())
                .kind(TriggerKind.CRAWL)
                .status(TriggerStatus.QUEUED)
                .origin(TriggerOrigin.MANUAL)
                .requestedBy(requestedBy)
                .requestedAt(OffsetDateTime.now())
                .build();
        when(triggerRequestQueue.enqueue(TriggerKind.CRAWL, TriggerOrigin.MANUAL, requestedBy))
                .thenReturn(queuedRow);

        TriggerRequest result = service.queue(TriggerKind.CRAWL, TriggerOrigin.MANUAL, requestedBy);

        assertThat(result.getStatus()).isEqualTo(TriggerStatus.QUEUED);
        verify(triggerRequestQueue).enqueue(TriggerKind.CRAWL, TriggerOrigin.MANUAL, requestedBy);
    }

    @Test
    @DisplayName("queue() defaults a null origin to MANUAL before delegating")
    void queueDefaultsNullOriginToManual() {
        TriggerRequest queuedRow = TriggerRequest.builder()
                .id(UUID.randomUUID())
                .kind(TriggerKind.ENRICHMENT)
                .status(TriggerStatus.QUEUED)
                .origin(TriggerOrigin.MANUAL)
                .requestedAt(OffsetDateTime.now())
                .build();
        when(triggerRequestQueue.enqueue(eq(TriggerKind.ENRICHMENT), eq(TriggerOrigin.MANUAL), any()))
                .thenReturn(queuedRow);

        service.queue(TriggerKind.ENRICHMENT, null, null);

        verify(triggerRequestQueue).enqueue(TriggerKind.ENRICHMENT, TriggerOrigin.MANUAL, null);
    }

    @Test
    @DisplayName("queue() propagates ConflictException from the port unchanged")
    void queuePropagatesConflictException() {
        when(triggerRequestQueue.enqueue(any(), any(), any()))
                .thenThrow(new ConflictException("A queued crawl request already exists"));

        assertThatThrownBy(() -> service.queue(TriggerKind.CRAWL, TriggerOrigin.MANUAL, null))
                .isInstanceOf(ConflictException.class);
    }

    // ── cancel() ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancel() returns the cancelled row when the port finds an active one")
    void cancelReturnsCancelledRow() {
        TriggerRequest cancelled = TriggerRequest.builder()
                .id(UUID.randomUUID())
                .kind(TriggerKind.CRAWL)
                .status(TriggerStatus.CANCELLED)
                .origin(TriggerOrigin.MANUAL)
                .requestedAt(OffsetDateTime.now())
                .finishedAt(OffsetDateTime.now())
                .build();
        when(triggerRequestQueue.cancelActive(TriggerKind.CRAWL)).thenReturn(Optional.of(cancelled));

        TriggerRequest result = service.cancel(TriggerKind.CRAWL);

        assertThat(result.getStatus()).isEqualTo(TriggerStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel() throws ResourceNotFoundException when no active row of the kind exists")
    void cancelThrowsWhenNoActiveRow() {
        when(triggerRequestQueue.cancelActive(TriggerKind.ENRICHMENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(TriggerKind.ENRICHMENT))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
