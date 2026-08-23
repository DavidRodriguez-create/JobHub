package com.davidcreate.jobhub.auth.adapter.out.notification;

import com.davidcreate.jobhub.auth.application.port.out.VerificationNotifier;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;

/**
 * Prod-only notifier that sends the verification code via SMTP through Quarkus Mailer.
 * Active only when the {@code prod} build profile is active; the {@code @DefaultBean}
 * {@link LoggingVerificationNotifier} is used in dev and test.
 *
 * SMTP is configured entirely via environment variables (see application-prod.properties).
 * No credentials are committed to source control.
 */
@ApplicationScoped
@IfBuildProfile("prod")
@RequiredArgsConstructor
public class MailerVerificationNotifier implements VerificationNotifier {

    private static final Logger LOG = Logger.getLogger(MailerVerificationNotifier.class);

    private final Mailer mailer;

    @Override
    public void sendActionCode(String email, VerificationAction action, String code) {
        String subject = buildSubject(action);
        String body = buildBody(action, code);
        mailer.send(Mail.withText(email, subject, body));
        LOG.infof("[verification-code] dispatched to=%s action=%s", email, action.value());
    }

    private String buildSubject(VerificationAction action) {
        return switch (action) {
            case VERIFY_EMAIL -> "JobHub — Verify your email address";
            case DELETE_ACCOUNT -> "JobHub — Confirm account deletion";
            case DELETE_ALL_APPLICATIONS -> "JobHub — Confirm deletion of all applications";
        };
    }

    private String buildBody(VerificationAction action, String code) {
        return switch (action) {
            case VERIFY_EMAIL -> """
                    Welcome to JobHub!

                    Your email verification code is: %s

                    This code expires in 15 minutes. Enter it on the verification screen to activate your account.

                    If you did not create a JobHub account, you can ignore this email.
                    """.formatted(code);
            case DELETE_ACCOUNT -> """
                    Your account deletion confirmation code is: %s

                    This code expires in 15 minutes. Enter it to permanently delete your account and all associated data.

                    If you did not request this, please ignore this email and your account will remain active.
                    """.formatted(code);
            case DELETE_ALL_APPLICATIONS -> """
                    Your confirmation code for deleting all applications is: %s

                    This code expires in 15 minutes.

                    If you did not request this, please ignore this email.
                    """.formatted(code);
        };
    }
}
