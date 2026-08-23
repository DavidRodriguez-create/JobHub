package com.davidcreate.jobhub.notification.component_tests;

import com.davidcreate.jobhub.notification.adapter.out.mail.QuteReminderMailer;
import com.davidcreate.jobhub.notification.domain.model.ReminderOffset;
import com.davidcreate.jobhub.notification.domain.model.UpcomingNextStep;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-210..TC-217: Verify that the restyled interview-reminder-email.html matches the
 * architect's palette and structural requirements.
 */
@QuarkusTest
@DisplayName("QuteReminderMailer Style Component Tests")
class QuteReminderMailerStyleComponentTest {

    @Inject
    QuteReminderMailer mailer;

    // TC-210: body background is #FAFAF7
    @Test
    @DisplayName("TC-210: reminder_email_body_background_is_fafaf7")
    void reminderEmailBodyBackgroundIsFafaf7() {
        String html = renderStep(ReminderOffset.H24);
        assertThat(html).contains("#FAFAF7");
    }

    // TC-211: brand primary colour #2950E3 present
    @Test
    @DisplayName("TC-211: reminder_email_brand_primary_colour_2950e3_is_present")
    void reminderEmailBrandPrimaryColour2950e3IsPresent() {
        String html = renderStep(ReminderOffset.H24);
        assertThat(html).containsIgnoringCase("#2950E3");
    }

    // TC-212: Geist font stack inlined, no @import
    @Test
    @DisplayName("TC-212: reminder_email_geist_font_stack_inlined_no_import")
    void reminderEmailGeistFontStackInlinedNoImport() {
        String html = renderStep(ReminderOffset.H24);
        assertThat(html).contains("Geist");
        assertThat(html).doesNotContain("@import");
    }

    // TC-213: hairline colour #E6E6DE present
    @Test
    @DisplayName("TC-213: reminder_email_hairline_colour_e6e6de_present")
    void reminderEmailHairlineColourE6e6dePresent() {
        String html = renderStep(ReminderOffset.H24);
        assertThat(html).containsIgnoringCase("#E6E6DE");
    }

    // TC-214: CTA button links to /applications/{applicationId}
    @Test
    @DisplayName("TC-214: reminder_email_cta_links_to_applications_applicationId")
    void reminderEmailCtaLinksToApplicationsApplicationId() {
        UUID appId = UUID.randomUUID();
        UpcomingNextStep step = buildStep(appId, "Phone Screen", LocalDate.now().plusDays(1), "Acme Corp");
        String html = mailer.render(step, ReminderOffset.H24);
        assertThat(html).contains("/applications/" + appId);
    }

    // TC-215: offsetLabel "24 hours before" appears for H24 offset
    @Test
    @DisplayName("TC-215: reminder_email_offset_label_24_hours_before_for_h24")
    void reminderEmailOffsetLabel24HoursBeforeForH24() {
        String html = renderStep(ReminderOffset.H24);
        assertThat(html).contains("24 hours before");
    }

    // TC-216: offsetLabel "1 hour before" appears for H1 offset
    @Test
    @DisplayName("TC-216: reminder_email_offset_label_1_hour_before_for_h1")
    void reminderEmailOffsetLabel1HourBeforeForH1() {
        String html = renderStep(ReminderOffset.H1);
        assertThat(html).contains("1 hour before");
    }

    // TC-217: footer contains manage-preferences link to /settings/notifications
    @Test
    @DisplayName("TC-217: reminder_email_footer_contains_manage_preferences_link")
    void reminderEmailFooterContainsManagePreferencesLink() {
        String html = renderStep(ReminderOffset.H24);
        assertThat(html).contains("/settings/notifications");
    }

    private String renderStep(ReminderOffset offset) {
        UpcomingNextStep step = buildStep(UUID.randomUUID(), "Technical Interview",
                LocalDate.now().plusDays(1), "Acme Corp");
        return mailer.render(step, offset);
    }

    private UpcomingNextStep buildStep(UUID applicationId, String label,
                                       LocalDate stepDate, String company) {
        return UpcomingNextStep.builder()
                .userId(UUID.randomUUID())
                .applicationId(applicationId)
                .label(label)
                .stepDate(stepDate)
                .reminderAt(OffsetDateTime.now().plusHours(1))
                .company(company)
                .status("interviewing")
                .build();
    }
}
