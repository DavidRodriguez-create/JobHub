package com.davidcreate.jobhub.notification.unit_tests.adapter.in.scheduler;

import com.davidcreate.jobhub.notification.adapter.in.scheduler.GhostedAlertScheduler;
import com.davidcreate.jobhub.notification.domain.port.in.ProcessGhostedAlertsUseCase;
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
@DisplayName("GhostedAlertScheduler Unit Tests")
class GhostedAlertSchedulerTest {

    @Mock
    ProcessGhostedAlertsUseCase processGhostedAlertsUseCase;

    // GA-NS-01: Scheduler delegates to use case when enabled
    @Test
    @DisplayName("GA-NS-01: scheduler delegates to use case when enabled")
    void delegatesToUseCaseWhenEnabled() {
        GhostedAlertScheduler scheduler = new GhostedAlertScheduler(processGhostedAlertsUseCase, true);

        scheduler.run();

        verify(processGhostedAlertsUseCase, times(1)).run();
    }

    // GA-NS-02: Scheduler skips use case when kill switch off
    @Test
    @DisplayName("GA-NS-02: scheduler skips use case when kill switch off")
    void skipsUseCaseWhenKillSwitchOff() {
        GhostedAlertScheduler scheduler = new GhostedAlertScheduler(processGhostedAlertsUseCase, false);

        scheduler.run();

        verify(processGhostedAlertsUseCase, never()).run();
    }

    // GA-NS-03: Scheduler swallows RuntimeException (service stays up)
    @Test
    @DisplayName("GA-NS-03: scheduler swallows RuntimeException so service stays up")
    void swallowsRuntimeExceptionSoServiceStaysUp() {
        doThrow(new RuntimeException("unexpected failure")).when(processGhostedAlertsUseCase).run();
        GhostedAlertScheduler scheduler = new GhostedAlertScheduler(processGhostedAlertsUseCase, true);

        assertDoesNotThrow(scheduler::run);
    }
}
