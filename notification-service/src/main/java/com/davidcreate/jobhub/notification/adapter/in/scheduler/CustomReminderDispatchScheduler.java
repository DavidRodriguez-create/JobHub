package com.davidcreate.jobhub.notification.adapter.in.scheduler;

import com.davidcreate.jobhub.notification.domain.port.in.DispatchDueCustomRemindersUseCase;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CustomReminderDispatchScheduler {

    private static final Logger LOG = Logger.getLogger(CustomReminderDispatchScheduler.class);

    private final DispatchDueCustomRemindersUseCase dispatchDueCustomRemindersUseCase;
    private final boolean enabled;

    public CustomReminderDispatchScheduler(DispatchDueCustomRemindersUseCase dispatchDueCustomRemindersUseCase,
                                            @ConfigProperty(name = "notification.custom-reminder.enabled", defaultValue = "true") boolean enabled) {
        this.dispatchDueCustomRemindersUseCase = dispatchDueCustomRemindersUseCase;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${notification.custom-reminder.cron}")
    public void run() {
        if (!enabled) {
            LOG.info("Custom reminder dispatch run skipped: notification.custom-reminder.enabled=false (disabled)");
            return;
        }

        try {
            dispatchDueCustomRemindersUseCase.run();
        } catch (RuntimeException e) {
            LOG.error("Custom reminder dispatch run failed unexpectedly", e);
        }
    }
}
