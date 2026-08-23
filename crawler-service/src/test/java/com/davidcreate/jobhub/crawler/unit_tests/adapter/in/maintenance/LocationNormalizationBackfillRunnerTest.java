package com.davidcreate.jobhub.crawler.unit_tests.adapter.in.maintenance;

import com.davidcreate.jobhub.crawler.adapter.in.maintenance.LocationNormalizationBackfillRunner;
import com.davidcreate.jobhub.crawler.domain.port.out.JobPostRepository;
import com.davidcreate.jobhub.crawler.domain.port.out.LocationBatchResult;
import io.quarkus.runtime.StartupEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Story #408 (ADR 0021), section J: mirrors {@code LanguagesBackfillRunnerTest} exactly, but
 * asserts the ascending-id cursor (not a page-0 loop), the load-bearing difference for this
 * runner (ADR 0021 section 6).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LocationNormalizationBackfillRunner Unit Tests")
class LocationNormalizationBackfillRunnerTest {

    @Mock
    JobPostRepository repository;

    private static final StartupEvent EVENT = new StartupEvent();

    private LocationNormalizationBackfillRunner runner(boolean enabled, int batchSize) {
        return new LocationNormalizationBackfillRunner(repository, enabled, batchSize);
    }

    @Test
    @DisplayName("QAE-408-R-01: disabled flag is a no-op, repository never touched")
    void r01_disabledIsNoOp() {
        runner(false, 100).onStart(EVENT);

        verify(repository, never()).normalizeLocationsBatch(any(), anyInt());
    }

    @Test
    @DisplayName("QAE-408-R-02: advances the ascending-id cursor across pages, stops at an empty page")
    void r02_advancesCursorAndStopsAtEmptyPage() {
        UUID lastIdFromPage1 = UUID.randomUUID();
        when(repository.normalizeLocationsBatch(isNull(), eq(50)))
                .thenReturn(new LocationBatchResult(lastIdFromPage1, 50));
        when(repository.normalizeLocationsBatch(eq(lastIdFromPage1), eq(50)))
                .thenReturn(LocationBatchResult.EMPTY);

        runner(true, 50).onStart(EVENT);

        verify(repository, times(1)).normalizeLocationsBatch(isNull(), eq(50));
        verify(repository, times(1)).normalizeLocationsBatch(eq(lastIdFromPage1), eq(50));
        verify(repository, times(2)).normalizeLocationsBatch(any(), eq(50));
    }

    @Test
    @DisplayName("QAE-408-R-03: MAX_BATCHES cap, stops after the constant number of iterations even if work remains")
    void r03_maxBatchesCap() {
        when(repository.normalizeLocationsBatch(any(), anyInt()))
                .thenReturn(new LocationBatchResult(UUID.randomUUID(), 10));

        runner(true, 10).onStart(EVENT);

        verify(repository, times(LocationNormalizationBackfillRunner.MAX_BATCHES))
                .normalizeLocationsBatch(any(), eq(10));
    }

    @Test
    @DisplayName("QAE-408-R-04: delegates ONLY to normalizeLocationsBatch, no other repository method invoked")
    void r04_delegatesOnlyToNormalizeLocationsBatch() {
        when(repository.normalizeLocationsBatch(any(), anyInt()))
                .thenReturn(LocationBatchResult.EMPTY);

        runner(true, 200).onStart(EVENT);

        verify(repository, times(1)).normalizeLocationsBatch(isNull(), eq(200));
        verifyNoMoreInteractions(repository);
    }
}
