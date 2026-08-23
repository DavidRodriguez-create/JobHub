package com.davidcreate.jobhub.notification.unit_tests.adapter.in.scheduler;

import com.davidcreate.jobhub.notification.adapter.in.scheduler.SecurityRecommendationScheduler;
import com.davidcreate.jobhub.notification.domain.port.in.ProcessSecurityRecommendationsUseCase;
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
@DisplayName("SecurityRecommendationScheduler Unit Tests")
class SecurityRecommendationSchedulerTest {

    @Mock
    ProcessSecurityRecommendationsUseCase processSecurityRecommendationsUseCase;

    @Test
    @DisplayName("scheduler delegates to use case when enabled")
    void delegatesToUseCaseWhenEnabled() {
        SecurityRecommendationScheduler scheduler =
                new SecurityRecommendationScheduler(processSecurityRecommendationsUseCase, true);

        scheduler.run();

        verify(processSecurityRecommendationsUseCase, times(1)).run();
    }

    @Test
    @DisplayName("scheduler skips use case when kill switch off")
    void skipsUseCaseWhenKillSwitchOff() {
        SecurityRecommendationScheduler scheduler =
                new SecurityRecommendationScheduler(processSecurityRecommendationsUseCase, false);

        scheduler.run();

        verify(processSecurityRecommendationsUseCase, never()).run();
    }

    @Test
    @DisplayName("scheduler swallows RuntimeException so service stays up")
    void swallowsRuntimeExceptionSoServiceStaysUp() {
        doThrow(new RuntimeException("unexpected failure")).when(processSecurityRecommendationsUseCase).run();
        SecurityRecommendationScheduler scheduler =
                new SecurityRecommendationScheduler(processSecurityRecommendationsUseCase, true);

        assertDoesNotThrow(scheduler::run);
    }
}
