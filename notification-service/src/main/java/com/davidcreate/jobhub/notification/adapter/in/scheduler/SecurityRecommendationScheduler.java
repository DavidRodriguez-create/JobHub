package com.davidcreate.jobhub.notification.adapter.in.scheduler;

import com.davidcreate.jobhub.notification.domain.port.in.ProcessSecurityRecommendationsUseCase;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SecurityRecommendationScheduler {

    private static final Logger LOG = Logger.getLogger(SecurityRecommendationScheduler.class);

    private final ProcessSecurityRecommendationsUseCase processSecurityRecommendationsUseCase;
    private final boolean enabled;

    public SecurityRecommendationScheduler(ProcessSecurityRecommendationsUseCase processSecurityRecommendationsUseCase,
                                            @ConfigProperty(name = "notification.security-recommendation.enabled", defaultValue = "true") boolean enabled) {
        this.processSecurityRecommendationsUseCase = processSecurityRecommendationsUseCase;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${notification.security-recommendation.cron}")
    public void run() {
        if (!enabled) {
            LOG.info("Security-recommendation run skipped: notification.security-recommendation.enabled=false (kill switch is off)");
            return;
        }

        try {
            processSecurityRecommendationsUseCase.run();
        } catch (RuntimeException e) {
            LOG.error("Security-recommendation run failed unexpectedly", e);
        }
    }
}
