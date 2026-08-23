package com.davidcreate.jobhub.notification.adapter.out.mail;

import com.davidcreate.jobhub.notification.domain.model.StaleApplication;
import com.davidcreate.jobhub.notification.domain.port.out.AlertMailer;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Year;

@ApplicationScoped
public class QuteAlertMailer implements AlertMailer {

    private static final Logger LOG = Logger.getLogger(QuteAlertMailer.class);

    private static final String SUBJECT = "Your application has been marked as Ghosted";

    private final Template ghostedAlertEmail;
    private final Mailer mailer;
    private final String jobhubBaseUrl;

    public QuteAlertMailer(@Location("ghosted-alert-email.html") Template ghostedAlertEmail,
                            Mailer mailer,
                            @ConfigProperty(name = "notification.digest.jobhub-base-url") String jobhubBaseUrl) {
        this.ghostedAlertEmail = ghostedAlertEmail;
        this.mailer = mailer;
        this.jobhubBaseUrl = jobhubBaseUrl;
    }

    @Override
    public void sendGhostedAlert(String recipientEmail, StaleApplication application) {
        String html = render(application);
        mailer.send(Mail.withHtml(recipientEmail, SUBJECT, html));
        LOG.infof("Ghosted-alert email sent to=%s applicationId=%s",
                recipientEmail, application.getId());
    }

    public String render(StaleApplication application) {
        return ghostedAlertEmail
                .data("company", application.getCompany())
                .data("jobTitle", application.getJobTitle())
                .data("daysSinceLastActivity", application.getDaysSinceLastActivity())
                .data("jobhubBaseUrl", jobhubBaseUrl)
                .data("currentYear", Year.now().getValue())
                .render();
    }
}
