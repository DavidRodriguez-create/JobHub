package com.davidcreate.jobhub.auth.adapter.out.notification;

import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Default notifier: logs the verification message instead of sending an email.
 * A real deployment replaces this with an email-provider adapter (same port).
 */
@ApplicationScoped
public class LoggingVerificationNotifier implements VerificationNotifier {

    private static final Logger LOG = Logger.getLogger(LoggingVerificationNotifier.class);

    @Override
    public void sendEmailVerification(String email, String token) {
        LOG.infof("[email-verification] to=%s token=%s", email, token);
    }

    @Override
    public void sendActionCode(String email, VerificationAction action, String code) {
        LOG.infof("[verification-code] to=%s action=%s code=%s", email, action.value(), code);
    }
}
