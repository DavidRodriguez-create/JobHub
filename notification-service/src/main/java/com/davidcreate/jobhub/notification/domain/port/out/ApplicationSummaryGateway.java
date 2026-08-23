package com.davidcreate.jobhub.notification.domain.port.out;

import com.davidcreate.jobhub.notification.domain.model.ApplicationSummary;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Outbound port to resolve a batch of application ids to their display summary
 * (company name + job title) on application-service's internal
 * {@code GET /internal/applications/summaries} endpoint (ADR 0014).
 */
public interface ApplicationSummaryGateway {

    /**
     * Resolves the given application ids to their summaries in a single batched call.
     * Ids that do not resolve (not found, not owned, or otherwise unresolvable) are
     * simply absent from the returned map: callers must treat a missing key as
     * unresolved, never as an error.
     *
     * @throws RuntimeException if the call fails (timeout, 5xx, connection error). Callers
     *         that want best-effort degradation (ADR 0014) must catch this themselves.
     */
    Map<UUID, ApplicationSummary> resolve(Set<UUID> applicationIds);
}
