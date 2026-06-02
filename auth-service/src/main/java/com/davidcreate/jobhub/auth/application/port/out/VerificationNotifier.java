package com.davidcreate.jobhub.auth.application.port.out;

import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;

/**
 * Outbound boundary for delivering verification messages to the user (email).
 * The default adapter logs; a real deployment swaps in an email provider.
 */
public interface VerificationNotifier {

    void sendEmailVerification(String email, String token);

    void sendActionCode(String email, VerificationAction action, String code);
}
