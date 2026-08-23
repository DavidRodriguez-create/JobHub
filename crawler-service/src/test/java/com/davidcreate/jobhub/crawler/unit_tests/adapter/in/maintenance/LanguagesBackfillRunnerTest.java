package com.davidcreate.jobhub.crawler.unit_tests.adapter.in.maintenance;

import com.davidcreate.jobhub.crawler.adapter.in.maintenance.LanguagesBackfillRunner;
import com.davidcreate.jobhub.crawler.domain.port.out.JobPostRepository;
import io.quarkus.runtime.StartupEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LanguagesBackfillRunner Unit Tests")
class LanguagesBackfillRunnerTest {

    @Mock
    JobPostRepository repository;

    private static final StartupEvent EVENT = new StartupEvent();

    private LanguagesBackfillRunner runner(boolean enabled, int batchSize) {
        return new LanguagesBackfillRunner(repository, enabled, batchSize);
    }

    @Test
    @DisplayName("BF-L-01: re-normalizes stored language arrays, no LLM — delegates only to normalizeLanguagesBatch")
    void bfL01_normalizesViaBatchWithNoLlm() {
        when(repository.normalizeLanguagesBatch(50))
                .thenReturn(50)
                .thenReturn(0);

        runner(true, 50).onStart(EVENT);

        verify(repository, times(2)).normalizeLanguagesBatch(50);
    }

    @Test
    @DisplayName("BF-L-02: disabled flag — onStart is a no-op, repository never called")
    void bfL02_disabledIsNoOp() {
        runner(false, 100).onStart(EVENT);

        verify(repository, never()).normalizeLanguagesBatch(anyInt());
    }

    @Test
    @DisplayName("BF-L-03: stops when a batch returns 0 rows — loop exits immediately")
    void bfL03_stopsAtZeroBatch() {
        when(repository.normalizeLanguagesBatch(anyInt())).thenReturn(0);

        runner(true, 200).onStart(EVENT);

        verify(repository, times(1)).normalizeLanguagesBatch(200);
    }

    @Test
    @DisplayName("BF-L-04: MAX_BATCHES cap — stops after the constant number of iterations even if work remains")
    void bfL04_maxBatchesCap() {
        when(repository.normalizeLanguagesBatch(anyInt())).thenReturn(10);

        runner(true, 10).onStart(EVENT);

        verify(repository, times(LanguagesBackfillRunner.MAX_BATCHES)).normalizeLanguagesBatch(10);
    }
}
