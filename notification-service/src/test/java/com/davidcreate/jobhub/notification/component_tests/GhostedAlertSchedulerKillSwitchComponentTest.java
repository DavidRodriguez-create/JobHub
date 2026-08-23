package com.davidcreate.jobhub.notification.component_tests;

import com.davidcreate.jobhub.notification.adapter.in.scheduler.GhostedAlertScheduler;
import com.davidcreate.jobhub.notification.component_tests.support.GhostedAlertDisabledProfile;
import com.davidcreate.jobhub.notification.component_tests.support.WireMockAppStaleResource;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * GA-NS-19: Kill switch off - stale query never made.
 * Uses GhostedAlertDisabledProfile to set notification.ghosted.enabled=false.
 * Must be separate class due to @TestProfile.
 * Also uses @InjectMock for AlertMailer (different beans from main test class).
 */
@QuarkusTest
@TestProfile(GhostedAlertDisabledProfile.class)
@QuarkusTestResource(WireMockAppStaleResource.class)
@DisplayName("GhostedAlertScheduler Component Tests - Kill Switch Off")
class GhostedAlertSchedulerKillSwitchComponentTest {

    @Inject
    GhostedAlertScheduler scheduler;

    @BeforeEach
    void resetWireMock() {
        appStale().resetAll();
    }

    @AfterEach
    void verifyNoStubInteractions() {
        // Nothing to clean - no notifications should have been written
    }

    private WireMockServer appStale() {
        return WireMockAppStaleResource.server();
    }

    // GA-NS-19: Kill switch off -> stale query never made
    @Test
    @DisplayName("GA-NS-19: kill switch off means stale query never called")
    void killSwitchOffStaleQueryNeverMade() {
        scheduler.run();

        // WireMock should not have received any GET /internal/applications/stale
        appStale().verify(0, getRequestedFor(urlPathEqualTo("/internal/applications/stale")));
    }
}
