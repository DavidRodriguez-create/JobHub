package com.davidcreate.jobhub.notification.adapter.in.scheduler;

import com.davidcreate.jobhub.notification.domain.port.in.SendInterviewRemindersUseCase;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class InterviewReminderScheduler {

    private static final Logger LOG = Logger.getLogger(InterviewReminderScheduler.class);

    private final SendInterviewRemindersUseCase sendInterviewRemindersUseCase;
    private final boolean enabled;

    public InterviewReminderScheduler(SendInterviewRemindersUseCase sendInterviewRemindersUseCase,
                                       @ConfigProperty(name = "notification.interview-reminder.enabled", defaultValue = "true") boolean enabled) {
        this.sendInterviewRemindersUseCase = sendInterviewRemindersUseCase;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${notification.interview-reminder.cron}")
    public void run() {
        if (!enabled) {
            LOG.info("Interview reminder run skipped: notification.interview-reminder.enabled=false (disabled)");
            return;
        }

        try {
            sendInterviewRemindersUseCase.run();
        } catch (RuntimeException e) {
            LOG.error("Interview reminder run failed unexpectedly", e);
        }
    }
}
