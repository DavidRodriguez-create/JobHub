package com.davidcreate.jobhub.crawler.component_tests.support;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Forces its own fresh application context, distinct from {@link ShutdownSignalIsolationProfile}
 * (Quarkus restarts whenever the active {@link QuarkusTestProfile} class changes). Both
 * {@code SchedulerShutdownComponentTest} and {@code ShutdownDrainComponentTest} fire a real
 * {@code ShutdownEvent}, which flips the shared {@code ShutdownSignal} adapter's flag
 * irreversibly for the lifetime of that context; sharing a single isolation profile between
 * the two would let whichever runs first poison the other.
 */
public class ShutdownDrainIsolationProfile implements QuarkusTestProfile {
}
