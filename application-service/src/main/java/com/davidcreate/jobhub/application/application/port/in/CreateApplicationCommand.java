package com.davidcreate.jobhub.application.application.port.in;

import java.util.UUID;

/**
 * Supply exactly one of {@code jobPostId} (apply to a crawled job post) or
 * {@code jobDetails} (manual entry).
 */
public record CreateApplicationCommand(
        UUID callerId,
        UUID jobPostId,
        JobDetailsCommand jobDetails) {
}
