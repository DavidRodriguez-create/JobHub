package com.davidcreate.jobhub.notification.adapter.in.scheduler;

import com.davidcreate.jobhub.notification.domain.port.in.ProcessGhostedAlertsUseCase;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class GhostedAlertScheduler {

    private static final Logger LOG = Logger.getLogger(GhostedAlertScheduler.class);

    private final ProcessGhostedAlertsUseCase processGhostedAlertsUseCase;
    private final boolean enabled;

    public GhostedAlertScheduler(ProcessGhostedAlertsUseCase processGhostedAlertsUseCase,
                                  @ConfigProperty(name = "notification.ghosted.enabled", defaultValue = "true") boolean enabled) {
        this.processGhostedAlertsUseCase = processGhostedAlertsUseCase;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${notification.ghosted.cron}")
    public void run() {
        if (!enabled) {
            LOG.info("Ghosted-alert run skipped: notification.ghosted.enabled=false (kill switch is off)");
            return;
        }

        try {
            processGhostedAlertsUseCase.run();
        } catch (RuntimeException e) {
            LOG.error("Ghosted-alert run failed unexpectedly", e);
        }
    }
}
