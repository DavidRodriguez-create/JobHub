package com.davidcreate.jobhub.notification.adapter.out.mail;

import com.davidcreate.jobhub.notification.domain.model.ReminderOffset;
import com.davidcreate.jobhub.notification.domain.model.UpcomingNextStep;
import com.davidcreate.jobhub.notification.domain.port.out.InterviewReminderMailer;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Year;

@ApplicationScoped
public class QuteReminderMailer implements InterviewReminderMailer {

    private static final Logger LOG = Logger.getLogger(QuteReminderMailer.class);

    private static final String SUBJECT = "JobHub Interview Reminder";

    private final Template interviewReminderEmail;
    private final Mailer mailer;
    private final String jobhubBaseUrl;

    public QuteReminderMailer(@Location("interview-reminder-email.html") Template interviewReminderEmail,
                               Mailer mailer,
                               @ConfigProperty(name = "notification.digest.jobhub-base-url") String jobhubBaseUrl) {
        this.interviewReminderEmail = interviewReminderEmail;
        this.mailer = mailer;
        this.jobhubBaseUrl = jobhubBaseUrl;
    }

    @Override
    public void send(String recipientEmail, UpcomingNextStep step, ReminderOffset offset) {
        String html = render(step, offset);
        mailer.send(Mail.withHtml(recipientEmail, SUBJECT, html));
        LOG.infof("Interview reminder email sent to=%s applicationId=%s offset=%s",
                recipientEmail, step.getApplicationId(), offset);
    }

    public String render(UpcomingNextStep step, ReminderOffset offset) {
        String offsetLabel = offset == ReminderOffset.H24 ? "24 hours before" : "1 hour before";
        return interviewReminderEmail
                .data("label", step.getLabel())
                .data("stepDate", step.getStepDate())
                .data("company", step.getCompany())
                .data("offsetLabel", offsetLabel)
                .data("applicationId", step.getApplicationId())
                .data("jobhubBaseUrl", jobhubBaseUrl)
                .data("currentYear", Year.now().getValue())
                .render();
    }
}
