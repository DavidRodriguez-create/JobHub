package com.davidcreate.jobhub.auth.application.port.in;

import java.util.UUID;

/**
 * Service-to-service request to verify one user's own 2FA code (ADR 0019).
 * {@code code} is optional: it is required only when the named user has 2FA enabled,
 * and ignored entirely when they do not.
 */
public record VerifyTwoFactorForServiceCommand(UUID userId, String code) {
}
