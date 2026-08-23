package com.davidcreate.jobhub.notification.adapter.out.mail;

import com.davidcreate.jobhub.notification.domain.exception.DigestSendException;
import com.davidcreate.jobhub.notification.domain.model.DigestJob;
import com.davidcreate.jobhub.notification.domain.port.out.DigestMailer;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Year;
import java.util.List;

@ApplicationScoped
public class QuteDigestMailer implements DigestMailer {

    private static final Logger LOG = Logger.getLogger(QuteDigestMailer.class);

    private static final String SUBJECT = "JobHub Weekly Digest";

    private final Template digestEmail;
    private final Mailer mailer;
    private final String jobhubBaseUrl;

    public QuteDigestMailer(@Location("digest-email.html") Template digestEmail,
                             Mailer mailer,
                             @ConfigProperty(name = "notification.digest.jobhub-base-url") String jobhubBaseUrl) {
        this.digestEmail = digestEmail;
        this.mailer = mailer;
        this.jobhubBaseUrl = jobhubBaseUrl;
    }

    @Override
    public void send(String recipientEmail, List<DigestJob> jobs, boolean personalised) {
        try {
            String html = render(jobs, personalised);
            mailer.send(Mail.withHtml(recipientEmail, SUBJECT, html));
            LOG.infof("Weekly digest email sent to=%s jobCount=%d personalised=%s",
                    recipientEmail, jobs.size(), personalised);
        } catch (RuntimeException e) {
            throw new DigestSendException("Failed to render/send weekly digest email", e);
        }
    }

    public String render(List<DigestJob> jobs, boolean personalised) {
        List<DigestJobView> jobViews = jobs.stream()
                .map(this::toView)
                .toList();

        return digestEmail.data(
                        "personalised", personalised,
                        "jobs", jobViews,
                        "jobhubBaseUrl", jobhubBaseUrl,
                        "currentYear", Year.now().getValue())
                .render();
    }

    private DigestJobView toView(DigestJob job) {
        return DigestJobView.builder()
                .id(job.getId().toString())
                .title(job.getTitle())
                .companyName(job.getCompanyName())
                .location(job.getLocation())
                .logoUrl(job.getCompanyLogoUrl() != null ? job.getCompanyLogoUrl().toString() : null)
                .build();
    }
}
