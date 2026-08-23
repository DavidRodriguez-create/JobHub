package com.davidcreate.jobhub.notification.component_tests;

import com.davidcreate.jobhub.notification.adapter.in.scheduler.CustomReminderDispatchScheduler;
import com.davidcreate.jobhub.notification.component_tests.support.CustomReminderDisabledProfile;
import com.davidcreate.jobhub.notification.domain.port.out.CustomReminderMailer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@QuarkusTest
@TestProfile(CustomReminderDisabledProfile.class)
@DisplayName("CustomReminderDispatchScheduler Kill Switch Component Tests")
class CustomReminderSchedulerKillSwitchComponentTest {

    @Inject
    CustomReminderDispatchScheduler scheduler;

    @Inject
    EntityManager em;

    @InjectMock
    CustomReminderMailer mailer;

    // CR-C-078
    @Test
    @DisplayName("CR-C-078: kill switch disabled -- run() is a no-op")
    void killSwitchDisabledRunIsNoOp() {
        String statusBefore = (String) em.createNativeQuery(
                "SELECT status FROM notification.custom_reminder WHERE id = 'ec000000-0000-0000-0000-000000000010'")
                .getSingleResult();

        scheduler.run();

        String statusAfter = (String) em.createNativeQuery(
                "SELECT status FROM notification.custom_reminder WHERE id = 'ec000000-0000-0000-0000-000000000010'")
                .getSingleResult();

        org.assertj.core.api.Assertions.assertThat(statusAfter).isEqualTo(statusBefore);
        verify(mailer, never()).send(anyString(), any());
    }
}
