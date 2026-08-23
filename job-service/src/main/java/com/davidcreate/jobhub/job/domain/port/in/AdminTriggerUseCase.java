package com.davidcreate.jobhub.job.domain.port.in;

import com.davidcreate.jobhub.job.domain.model.TriggerCommand;
import com.davidcreate.jobhub.job.domain.model.TriggerKind;
import com.davidcreate.jobhub.job.domain.model.TriggerRequest;
import com.davidcreate.jobhub.job.domain.model.TriggerStatusOverview;

import java.util.UUID;

public interface AdminTriggerUseCase {

    /**
     * Validates the command, applies the enabled/2FA/dedupe gates (in that order),
     * and records a {@code queued} row.
     *
     * @throws com.davidcreate.jobhub.job.domain.exception.TriggeringDisabledException
     *         when {@code jobhub.admin.trigger.enabled=false}
     * @throws jakarta.ws.rs.BadRequestException
     *         when {@code kind} is unknown or {@code code} is malformed
     * @throws com.davidcreate.jobhub.job.domain.exception.VerificationRequiredException
     *         when the admin has 2FA enabled and the code is missing/invalid/expired/used
     * @throws com.davidcreate.jobhub.job.domain.exception.VerificationThrottledException
     *         when auth-service throttles the 2FA verify call
     * @throws com.davidcreate.jobhub.job.domain.exception.TriggerInProgressException
     *         when crawler-service rejects the request because a same-kind row is
     *         already queued (ADR 0033)
     * @throws com.davidcreate.jobhub.job.domain.exception.CrawlerUnavailableException
     *         when crawler-service cannot be reached; nothing was started
     */
    TriggerRequest queue(TriggerCommand command);

    /**
     * Current toggle config + most-recent run per kind, plus whether the given
     * admin (the caller) has 2FA enabled on their own account (ADR 0019).
     *
     * @param adminId the caller's own user id (the JWT {@code sub})
     */
    TriggerStatusOverview getStatus(UUID adminId);

    /**
     * Cancels the active (queued or running) trigger request for the given kind
     * (ADR 0006). A {@code queued} row transitions immediately to {@code cancelled};
     * a {@code running} row transitions to {@code cancel_requested} for
     * crawler-service to finalize. Never gated by 2FA (BR-384-7).
     *
     * @throws com.davidcreate.jobhub.job.domain.exception.NoActiveTriggerException
     *         when crawler-service reports no {@code queued}/{@code running} row for
     *         this kind (its internal 404, ADR 0033)
     * @throws com.davidcreate.jobhub.job.domain.exception.CrawlerUnavailableException
     *         when crawler-service cannot be reached; nothing was changed
     */
    TriggerRequest cancel(TriggerKind kind);
}
