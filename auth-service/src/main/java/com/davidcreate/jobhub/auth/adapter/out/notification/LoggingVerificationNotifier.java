package com.davidcreate.jobhub.auth.adapter.out.notification;

import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Default notifier active in dev and test: logs the verification code instead of
 * sending an email. The prod profile swaps in MailerVerificationNotifier via
 * {@code @IfBuildProfile("prod")}, making this bean inactive there via {@code @DefaultBean}.
 */
@ApplicationScoped
@DefaultBean
public class LoggingVerificationNotifier implements VerificationNotifier {

    private static final Logger LOG = Logger.getLogger(LoggingVerificationNotifier.class);

    @Override
    public void sendActionCode(String email, VerificationAction action, String code) {
        LOG.infof("[verification-code] to=%s action=%s code=%s", email, action.value(), code);
    }
}
