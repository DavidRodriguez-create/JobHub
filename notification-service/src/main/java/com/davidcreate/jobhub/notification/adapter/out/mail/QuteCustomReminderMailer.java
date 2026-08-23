package com.davidcreate.jobhub.notification.adapter.out.mail;

import com.davidcreate.jobhub.notification.domain.model.CustomReminder;
import com.davidcreate.jobhub.notification.domain.port.out.CustomReminderMailer;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Year;

@ApplicationScoped
public class QuteCustomReminderMailer implements CustomReminderMailer {

    private static final Logger LOG = Logger.getLogger(QuteCustomReminderMailer.class);

    private static final String SUBJECT_PREFIX = "JobHub Reminder: ";

    private final Template customReminderEmail;
    private final Mailer mailer;
    private final String jobhubBaseUrl;

    public QuteCustomReminderMailer(@Location("custom-reminder-email.html") Template customReminderEmail,
                                     Mailer mailer,
                                     @ConfigProperty(name = "notification.digest.jobhub-base-url") String jobhubBaseUrl) {
        this.customReminderEmail = customReminderEmail;
        this.mailer = mailer;
        this.jobhubBaseUrl = jobhubBaseUrl;
    }

    @Override
    public void send(String recipientEmail, CustomReminder reminder) {
        String html = render(reminder);
        mailer.send(Mail.withHtml(recipientEmail, SUBJECT_PREFIX + reminder.getTitle(), html));
        LOG.infof("Custom reminder email sent to=%s reminderId=%s", recipientEmail, reminder.getId());
    }

    public String render(CustomReminder reminder) {
        return customReminderEmail
                .data("title", reminder.getTitle())
                .data("note", reminder.getNote() != null && !reminder.getNote().isBlank() ? reminder.getNote() : null)
                .data("applicationId", reminder.getApplicationId())
                .data("jobhubBaseUrl", jobhubBaseUrl)
                .data("currentYear", Year.now().getValue())
                .render();
    }
}
