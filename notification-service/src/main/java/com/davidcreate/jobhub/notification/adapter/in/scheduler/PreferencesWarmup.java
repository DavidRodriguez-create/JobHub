package com.davidcreate.jobhub.notification.adapter.in.scheduler;

import com.davidcreate.jobhub.notification.domain.port.in.GetPreferencesUseCase;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

import java.util.UUID;

@ApplicationScoped
public class PreferencesWarmup {

    private static final Logger LOG = Logger.getLogger(PreferencesWarmup.class);

    // Sentinel UUID guaranteed not to exist in the preferences table; the use case returns
    // defaults for a missing row without writing.
    private static final UUID WARMUP_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final GetPreferencesUseCase getPreferencesUseCase;

    public PreferencesWarmup(GetPreferencesUseCase getPreferencesUseCase) {
        this.getPreferencesUseCase = getPreferencesUseCase;
    }

    void onStart(@Observes StartupEvent event) {
        // Skipped under tests: @QuarkusTest classes that switch test profiles already pay the
        // startup cost on each restart, and an extra observer can perturb container boot.
        if (LaunchMode.current() == LaunchMode.TEST) {
            return;
        }
        try {
            getPreferencesUseCase.getPreferences(WARMUP_UUID);
            LOG.info("PreferencesWarmup: preferences code path warmed successfully");
        } catch (Exception e) {
            LOG.warnf("PreferencesWarmup: warmup call failed (non-fatal): %s", e.getMessage());
        }
    }
}
