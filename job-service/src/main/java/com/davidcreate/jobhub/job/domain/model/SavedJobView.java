package com.davidcreate.jobhub.job.domain.model;

import java.time.OffsetDateTime;

/**
 * A saved-job bookmark paired with the live job posting it points to — the shape
 * the saved-jobs list returns.
 */
public record SavedJobView(OffsetDateTime savedAt, JobPost job) {
}
