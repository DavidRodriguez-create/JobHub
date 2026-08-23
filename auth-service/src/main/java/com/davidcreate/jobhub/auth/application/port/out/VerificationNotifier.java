package com.davidcreate.jobhub.auth.application.port.out;

import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;

/**
 * Outbound boundary for delivering verification messages to the user (email).
 * The dev/test adapter logs; the prod adapter sends via Quarkus Mailer.
 */
public interface VerificationNotifier {

    void sendActionCode(String email, VerificationAction action, String code);
}
