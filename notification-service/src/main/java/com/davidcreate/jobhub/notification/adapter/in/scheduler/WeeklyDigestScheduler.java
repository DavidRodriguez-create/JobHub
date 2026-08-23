package com.davidcreate.jobhub.notification.adapter.in.scheduler;

import com.davidcreate.jobhub.notification.domain.port.in.SendWeeklyDigestUseCase;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class WeeklyDigestScheduler {

    private static final Logger LOG = Logger.getLogger(WeeklyDigestScheduler.class);

    private final SendWeeklyDigestUseCase sendWeeklyDigestUseCase;
    private final boolean enabled;

    public WeeklyDigestScheduler(SendWeeklyDigestUseCase sendWeeklyDigestUseCase,
                                  @ConfigProperty(name = "notification.digest.enabled", defaultValue = "true") boolean enabled) {
        this.sendWeeklyDigestUseCase = sendWeeklyDigestUseCase;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${notification.digest.cron}")
    public void run() {
        if (!enabled) {
            LOG.info("Weekly digest run skipped — notification.digest.enabled=false (disabled)");
            return;
        }

        try {
            sendWeeklyDigestUseCase.run();
        } catch (RuntimeException e) {
            LOG.error("Weekly digest run failed unexpectedly", e);
        }
    }
}
