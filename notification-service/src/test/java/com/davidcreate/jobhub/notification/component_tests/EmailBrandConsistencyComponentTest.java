package com.davidcreate.jobhub.notification.component_tests;

import com.davidcreate.jobhub.notification.adapter.out.mail.QuteAlertMailer;
import com.davidcreate.jobhub.notification.adapter.out.mail.QuteDigestMailer;
import com.davidcreate.jobhub.notification.adapter.out.mail.QuteReminderMailer;
import com.davidcreate.jobhub.notification.domain.model.DigestJob;
import com.davidcreate.jobhub.notification.domain.model.ReminderOffset;
import com.davidcreate.jobhub.notification.domain.model.StaleApplication;
import com.davidcreate.jobhub.notification.domain.model.UpcomingNextStep;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-224..TC-225: Cross-mailer brand-consistency assertions.
 * All three email templates must share the same palette tokens.
 */
@QuarkusTest
@DisplayName("Email Brand Consistency Component Tests")
class EmailBrandConsistencyComponentTest {

    @Inject
    QuteDigestMailer digestMailer;

    @Inject
    QuteReminderMailer reminderMailer;

    @Inject
    QuteAlertMailer alertMailer;

    // TC-224: all three mailers use #2950E3 brand primary
    @Test
    @DisplayName("TC-224: all_three_emails_share_brand_primary_2950e3")
    void allThreeEmailsShareBrandPrimary2950e3() {
        String digestHtml = digestMailer.render(List.of(buildDigestJob()), true);
        String reminderHtml = reminderMailer.render(buildStep(), ReminderOffset.H24);
        String alertHtml = alertMailer.render(buildAlert());

        assertThat(digestHtml).as("digest").containsIgnoringCase("#2950E3");
        assertThat(reminderHtml).as("reminder").containsIgnoringCase("#2950E3");
        assertThat(alertHtml).as("alert").containsIgnoringCase("#2950E3");
    }

    // TC-225: all three mailers use #FAFAF7 body background
    @Test
    @DisplayName("TC-225: all_three_emails_share_body_background_fafaf7")
    void allThreeEmailsShareBodyBackgroundFafaf7() {
        String digestHtml = digestMailer.render(List.of(buildDigestJob()), false);
        String reminderHtml = reminderMailer.render(buildStep(), ReminderOffset.H1);
        String alertHtml = alertMailer.render(buildAlert());

        assertThat(digestHtml).as("digest").contains("#FAFAF7");
        assertThat(reminderHtml).as("reminder").contains("#FAFAF7");
        assertThat(alertHtml).as("alert").contains("#FAFAF7");
    }

    private DigestJob buildDigestJob() {
        return DigestJob.builder()
                .id(UUID.randomUUID())
                .title("Engineer")
                .companyName("TestCo")
                .location("Remote")
                .companyLogoUrl(null)
                .build();
    }

    private UpcomingNextStep buildStep() {
        return UpcomingNextStep.builder()
                .userId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .label("Technical Interview")
                .stepDate(LocalDate.now().plusDays(1))
                .reminderAt(OffsetDateTime.now().plusHours(2))
                .company("TestCo")
                .status("interviewing")
                .build();
    }

    private StaleApplication buildAlert() {
        return StaleApplication.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .jobTitle("Engineer")
                .company("TestCo")
                .daysSinceLastActivity(14)
                .build();
    }
}
