package com.davidcreate.jobhub.notification.component_tests;

import com.davidcreate.jobhub.notification.adapter.out.mail.QuteCustomReminderMailer;
import com.davidcreate.jobhub.notification.domain.model.CustomReminder;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderChannel;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Style + addendum A2 (note-fallback) verification for the custom-reminder-email.html template.
 */
@QuarkusTest
@DisplayName("QuteCustomReminderMailer Style Component Tests")
class QuteCustomReminderMailerStyleComponentTest {

    @Inject
    QuteCustomReminderMailer mailer;

    private CustomReminder reminder(String title, String note) {
        return CustomReminder.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .title(title)
                .note(note)
                .triggerAtUtc(Instant.now().plusSeconds(3600))
                .channels(Set.of(CustomReminderChannel.EMAIL))
                .status(CustomReminderStatus.SCHEDULED)
                .build();
    }

    @Test
    @DisplayName("custom_reminder_email_body_background_is_fafaf7")
    void bodyBackgroundIsFafaf7() {
        String html = mailer.render(reminder("Prep call", "Bring portfolio"));
        assertThat(html).contains("#FAFAF7");
    }

    @Test
    @DisplayName("custom_reminder_email_brand_primary_colour_2950e3_is_present")
    void brandPrimaryColourPresent() {
        String html = mailer.render(reminder("Prep call", "Bring portfolio"));
        assertThat(html).containsIgnoringCase("#2950E3");
    }

    @Test
    @DisplayName("custom_reminder_email_cta_links_to_applications_applicationId")
    void ctaLinksToApplication() {
        CustomReminder r = reminder("Prep call", "Bring portfolio");
        String html = mailer.render(r);
        assertThat(html).contains("/applications/" + r.getApplicationId());
    }

    @Test
    @DisplayName("custom_reminder_email_footer_contains_manage_preferences_link")
    void footerContainsManagePreferencesLink() {
        String html = mailer.render(reminder("Prep call", "Bring portfolio"));
        assertThat(html).contains("/settings/notifications");
    }

    // ADR addendum A2: when note is present, body shows the title and the note.
    @Test
    @DisplayName("ADR-A2: body shows title and note when note is present")
    void bodyShowsTitleAndNoteWhenPresent() {
        String html = mailer.render(reminder("Prep call", "Bring portfolio"));
        assertThat(html).contains("Prep call");
        assertThat(html).contains("Bring portfolio");
    }

    // ADR addendum A2: when note is null, body shows the title only, no parenthetical filler.
    @Test
    @DisplayName("ADR-A2: body shows title only (no filler) when note is null")
    void bodyShowsTitleOnlyWhenNoteIsNull() {
        String html = mailer.render(reminder("Prep call", null));
        assertThat(html).contains("Prep call");
        assertThat(html.toLowerCase()).doesNotContain("no extra details");
        assertThat(html.toLowerCase()).doesNotContain("no note");
    }

    // ADR addendum A2: blank note (after trim) behaves the same as null.
    @Test
    @DisplayName("ADR-A2: blank note behaves the same as null (title only, no filler)")
    void bodyShowsTitleOnlyWhenNoteIsBlank() {
        String html = mailer.render(reminder("Prep call", "   "));
        assertThat(html).contains("Prep call");
        assertThat(html.toLowerCase()).doesNotContain("no extra details");
    }
}
