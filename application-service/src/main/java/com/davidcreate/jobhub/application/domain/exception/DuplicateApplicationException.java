package com.davidcreate.jobhub.application.domain.exception;

import java.util.UUID;

/**
 * Raised when a user tries to apply to a crawled job post they already have an
 * application for. A given user may hold at most one application per job post.
 */
public class DuplicateApplicationException extends RuntimeException {
    public DuplicateApplicationException(UUID jobPostId) {
        super("an application already exists for job post " + jobPostId);
    }
}
