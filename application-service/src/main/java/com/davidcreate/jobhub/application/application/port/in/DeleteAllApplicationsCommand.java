package com.davidcreate.jobhub.application.application.port.in;

import java.util.UUID;

/**
 * Delete every application owned by the caller, after auth-service validates and consumes
 * the supplied verification code. {@code bearerToken} is the caller's raw JWT, forwarded
 * to auth-service so it can identify the user and consume the code on their behalf.
 */
public record DeleteAllApplicationsCommand(
        UUID callerId,
        UUID verificationId,
        String code,
        String bearerToken) {
}
