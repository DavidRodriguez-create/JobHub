package com.davidcreate.jobhub.notification.domain.port.in;

/**
 * Use-case interface for the security-recommendation run (Story #133, ADR 0012).
 * The scheduler fires this periodically; the implementation polls auth-service
 * for recently registered users without 2FA enabled and writes a one-time
 * SECURITY_RECOMMENDATION notification for each one that has not already received it.
 */
public interface ProcessSecurityRecommendationsUseCase {

    void run();
}
