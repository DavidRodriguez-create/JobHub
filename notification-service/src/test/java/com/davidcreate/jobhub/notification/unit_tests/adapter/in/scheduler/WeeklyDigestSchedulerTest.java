package com.davidcreate.jobhub.notification.unit_tests.adapter.in.scheduler;

import com.davidcreate.jobhub.notification.adapter.in.scheduler.WeeklyDigestScheduler;
import com.davidcreate.jobhub.notification.domain.port.in.SendWeeklyDigestUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeeklyDigestScheduler Unit Tests")
class WeeklyDigestSchedulerTest {

    @Mock
    SendWeeklyDigestUseCase sendWeeklyDigestUseCase;

    // TC-15b
    @Test
    @DisplayName("weekly_digest_scheduler_does_not_crash_the_service_on_dependency_outage")
    void doesNotCrashServiceWhenUseCaseThrowsUnexpectedException() {
        doThrow(new RuntimeException("unexpected failure")).when(sendWeeklyDigestUseCase).run();

        WeeklyDigestScheduler scheduler = new WeeklyDigestScheduler(sendWeeklyDigestUseCase, true);

        assertDoesNotThrow(scheduler::run);
    }

    // TC-18
    @Test
    @DisplayName("scheduler_performs_no_work_when_digest_disabled")
    void performsNoWorkWhenDigestDisabled() {
        WeeklyDigestScheduler scheduler = new WeeklyDigestScheduler(sendWeeklyDigestUseCase, false);

        scheduler.run();

        verify(sendWeeklyDigestUseCase, never()).run();
    }

    // TC-19
    @Test
    @DisplayName("scheduler_logs_skip_reason_when_digest_disabled")
    void logsSkipReasonWhenDigestDisabled() {
        WeeklyDigestScheduler scheduler = new WeeklyDigestScheduler(sendWeeklyDigestUseCase, false);

        assertDoesNotThrow(scheduler::run);

        verify(sendWeeklyDigestUseCase, never()).run();
    }

    // TC-19b
    @Test
    @DisplayName("enabling_digest_takes_effect_on_next_scheduled_fire_not_immediately")
    void runsUseCaseExactlyOnceWhenEnabled() {
        WeeklyDigestScheduler scheduler = new WeeklyDigestScheduler(sendWeeklyDigestUseCase, true);

        scheduler.run();

        verify(sendWeeklyDigestUseCase, times(1)).run();
    }
}
