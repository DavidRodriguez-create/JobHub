package com.davidcreate.jobhub.auth.application.port.in;

import java.util.UUID;

/**
 * Service-to-service, side-effect-free query: does this user have TOTP two-factor
 * authentication enabled and verified? Consumed by job-service to populate the
 * {@code twoFactorRequired} signal on the admin crawl/enrichment trigger status (ADR 0019).
 */
public interface GetTwoFactorStatusUseCase {

    /**
     * @throws com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException if no user
     *         exists for {@code userId}
     */
    boolean getStatus(UUID userId);
}
