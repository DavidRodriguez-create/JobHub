package com.davidcreate.jobhub.crawler.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Forces its own fresh application context (Quarkus restarts whenever the active
 * {@link QuarkusTestProfile} changes, matching {@link LocationBackfillIsolationProfile}).
 * {@code SchedulerShutdownComponentTest} fires a real {@code ShutdownEvent}, which flips the
 * shared {@code ShutdownSignal} adapter's flag irreversibly for the lifetime of that context
 * -- it must not share the default-profile instance with every other component test class in
 * this module, or every scheduler tick after it would silently no-op for the rest of the suite.
 */
public class ShutdownSignalIsolationProfile implements QuarkusTestProfile {
}
