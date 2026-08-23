package com.davidcreate.jobhub.notification.domain.port.out;

import com.davidcreate.jobhub.notification.domain.exception.ApplicationAlreadyGhostedException;
import com.davidcreate.jobhub.notification.domain.model.StaleApplication;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port for stale-application operations on application-service.
 */
public interface StaleApplicationGateway {

    /**
     * Returns non-terminal applications whose updatedAt is older than {@code days} days.
     *
     * @throws RuntimeException if the call fails (timeout, 5xx, connection error).
     */
    List<StaleApplication> listStaleApplications(int days);

    /**
     * Updates the application's status to ghosted via the internal service endpoint.
     *
     * @throws ApplicationAlreadyGhostedException if application-service returns 409 (already terminal).
     * @throws RuntimeException for other non-2xx responses or connection failures.
     */
    void updateApplicationStatusToGhosted(UUID applicationId);
}
