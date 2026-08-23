package com.davidcreate.jobhub.notification.domain.port.out;

import com.davidcreate.jobhub.notification.domain.model.DigestJob;

import java.util.List;

public interface DigestMailer {

    /**
     * Renders and sends the weekly digest email to the given address.
     *
     * @param recipientEmail destination address
     * @param jobs           jobs to include as cards, in display order
     * @param personalised   {@code true} for the "matching your interests" framing,
     *                       {@code false} for the generic "top jobs this week" framing
     * @throws com.davidcreate.jobhub.notification.domain.exception.DigestSendException
     *         if rendering or sending fails
     */
    void send(String recipientEmail, List<DigestJob> jobs, boolean personalised);
}
