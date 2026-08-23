package com.davidcreate.jobhub.notification.domain.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;

/**
 * CDI producer for {@link Clock}. Provides a UTC system clock to beans that
 * require it (e.g. {@link InterviewReminderService}) so the clock can be replaced
 * in unit tests without Quarkus CDI involved.
 */
@ApplicationScoped
public class ClockProducer {

    @Produces
    @ApplicationScoped
    public Clock utcClock() {
        return Clock.systemUTC();
    }
}
