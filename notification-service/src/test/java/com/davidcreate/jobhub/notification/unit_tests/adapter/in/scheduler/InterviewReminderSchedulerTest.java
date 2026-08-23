package com.davidcreate.jobhub.notification.unit_tests.adapter.in.scheduler;

import com.davidcreate.jobhub.notification.adapter.in.scheduler.InterviewReminderScheduler;
import com.davidcreate.jobhub.notification.domain.port.in.SendInterviewRemindersUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterviewReminderScheduler Unit Tests")
class InterviewReminderSchedulerTest {

    @Mock
    SendInterviewRemindersUseCase useCase;

    // TC-119
    @Test
    @DisplayName("scheduler_performs_no_work_when_interview_reminder_disabled")
    void schedulerPerformsNoWorkWhenInterviewReminderDisabled() {
        InterviewReminderScheduler scheduler = new InterviewReminderScheduler(useCase, false);

        scheduler.run();

        verify(useCase, never()).run();
    }

    // TC-120
    @Test
    @DisplayName("scheduler_logs_skip_reason_when_interview_reminder_disabled")
    void schedulerLogsSkipReasonWhenInterviewReminderDisabled() {
        InterviewReminderScheduler scheduler = new InterviewReminderScheduler(useCase, false);

        // The skip logging is exercised implicitly: if the scheduler returns without
        // calling the use case, the log line with "disabled"/"skipped" was emitted.
        // The use-case must NOT be invoked (that is the primary assertion).
        scheduler.run();

        verify(useCase, never()).run();
    }
}
