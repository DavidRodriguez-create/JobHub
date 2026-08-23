package com.davidcreate.jobhub.notification.domain.port.out;

import com.davidcreate.jobhub.notification.domain.model.StaleApplication;

/**
 * Outbound port for sending ghosted-alert notification emails.
 */
public interface AlertMailer {

    /**
     * Sends a ghosted-alert email to the specified recipient.
     *
     * @param recipientEmail the verified email address of the user
     * @param application    the stale application context (job title, company, days)
     * @throws RuntimeException if sending fails
     */
    void sendGhostedAlert(String recipientEmail, StaleApplication application);
}
