package com.davidcreate.jobhub.notification.domain.port.in;

public interface SendWeeklyDigestUseCase {

    /**
     * Runs one weekly-digest pass: selects opted-in candidates, fetches their
     * interest profile and matching jobs, sends the digest email, and records
     * a {@code digest_run} row per processed user. Never throws — per-user and
     * whole-run failures are logged and recorded, not propagated.
     */
    void run();
}
