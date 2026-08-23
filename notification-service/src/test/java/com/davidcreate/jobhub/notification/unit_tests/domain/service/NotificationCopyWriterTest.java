package com.davidcreate.jobhub.notification.unit_tests.domain.service;

import com.davidcreate.jobhub.notification.domain.model.ReminderOffset;
import com.davidcreate.jobhub.notification.domain.service.NotificationCopyWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationCopyWriter Unit Tests")
class NotificationCopyWriterTest {

    private final NotificationCopyWriter copyWriter = new NotificationCopyWriter();

    // CW-U-01
    @Test
    @DisplayName("CW-U-01: ghosted title is the fixed playful string")
    void ghostedTitleIsFixedPlayfulString() {
        assertThat(copyWriter.ghostedAlertTitle()).isEqualTo("👻 A wild ghost appeared!");
    }

    // CW-U-02
    @Test
    @DisplayName("CW-U-02: ghosted body interpolates jobTitle when present")
    void ghostedBodyInterpolatesJobTitleWhenPresent() {
        String message = copyWriter.ghostedAlertMessage("Senior Backend Engineer");

        assertThat(message).isEqualTo("Your application Senior Backend Engineer seems to have disappeared "
                + "into the hiring void. If you're still interested in the position, a quick follow-up "
                + "with the recruiter could bring it back to life. Don't give up! Your next opportunity "
                + "might be just around the corner.");
    }

    // CW-U-03
    @Test
    @DisplayName("CW-U-03: ghosted body falls back gracefully when jobTitle is null")
    void ghostedBodyFallsBackWhenJobTitleNull() {
        String message = copyWriter.ghostedAlertMessage(null);

        assertThat(message).isEqualTo("Your application seems to have disappeared into the hiring void. "
                + "If you're still interested in the position, a quick follow-up with the recruiter could "
                + "bring it back to life. Don't give up! Your next opportunity might be just around the corner.");
        assertThat(message.toLowerCase()).doesNotContain("null").doesNotContain("undefined");
        assertThat(message).doesNotContain("  ");
    }

    // CW-U-04
    @Test
    @DisplayName("CW-U-04: ghosted body falls back gracefully when jobTitle is blank")
    void ghostedBodyFallsBackWhenJobTitleBlank() {
        String message = copyWriter.ghostedAlertMessage("   ");

        assertThat(message).isEqualTo(copyWriter.ghostedAlertMessage(null));
    }

    // CW-U-05
    @Test
    @DisplayName("CW-U-05: H24 title is the fixed playful string")
    void h24TitleIsFixedPlayfulString() {
        assertThat(copyWriter.interviewReminderTitle(ReminderOffset.H24)).isEqualTo("⏰ Countdown to showtime!");
    }

    // CW-U-06
    @Test
    @DisplayName("CW-U-06: H1 title is the fixed playful string, distinct from H24")
    void h1TitleIsFixedPlayfulStringDistinctFromH24() {
        String h1Title = copyWriter.interviewReminderTitle(ReminderOffset.H1);

        assertThat(h1Title).isEqualTo("🎤 You're up soon!");
        assertThat(h1Title).isNotEqualTo(copyWriter.interviewReminderTitle(ReminderOffset.H24));
    }

    // CW-U-07
    @Test
    @DisplayName("CW-U-07: H24 body interpolates label and company when both present")
    void h24BodyInterpolatesLabelAndCompanyWhenBothPresent() {
        String message = copyWriter.interviewReminderMessage(
                "Interview with Product Manager", "Acme Corp", "2026-06-18", ReminderOffset.H24);

        assertThat(message).isEqualTo("Your Interview with Product Manager with Acme Corp is coming up "
                + "in about 24 hours (2026-06-18). Take a breath, review your notes, and go show them "
                + "what you've got.");
    }

    // CW-U-08
    @Test
    @DisplayName("CW-U-08: H1 body interpolates label and company when both present")
    void h1BodyInterpolatesLabelAndCompanyWhenBothPresent() {
        String message = copyWriter.interviewReminderMessage(
                "Interview with Product Manager", "Acme Corp", "2026-06-18", ReminderOffset.H1);

        assertThat(message).isEqualTo("Your Interview with Product Manager with Acme Corp kicks off "
                + "in about 1 hour (2026-06-18). Grab some water, take a breath, you've got this.");
    }

    // CW-U-09
    @Test
    @DisplayName("CW-U-09: H24 body falls back when company is absent")
    void h24BodyFallsBackWhenCompanyAbsent() {
        String message = copyWriter.interviewReminderMessage(
                "Interview with Product Manager", null, "2026-06-18", ReminderOffset.H24);

        assertThat(message).isEqualTo("Your Interview with Product Manager is coming up in about 24 hours "
                + "(2026-06-18). Take a breath, review your notes, and go show them what you've got.");
        assertThat(message.toLowerCase()).doesNotContain("with null").doesNotContain("with undefined");
        assertThat(message).doesNotContain("  ");
    }

    // CW-U-10
    @Test
    @DisplayName("CW-U-10: H1 body falls back when label is absent")
    void h1BodyFallsBackWhenLabelAbsent() {
        String message = copyWriter.interviewReminderMessage(
                null, "Acme Corp", "2026-06-18", ReminderOffset.H1);

        assertThat(message).isEqualTo("Your interview with Acme Corp kicks off in about 1 hour "
                + "(2026-06-18). Grab some water, take a breath, you've got this.");
        assertThat(message).contains("interview");
    }

    // CW-U-11
    @Test
    @DisplayName("CW-U-11: H24 body falls back when both label and company are absent")
    void h24BodyFallsBackWhenBothLabelAndCompanyAbsent() {
        String message = copyWriter.interviewReminderMessage(null, null, "2026-06-18", ReminderOffset.H24);

        assertThat(message).isEqualTo("Your interview is coming up in about 24 hours (2026-06-18). "
                + "Take a breath, review your notes, and go show them what you've got.");
    }

    // CW-U-12
    @Test
    @DisplayName("CW-U-12: H1 body falls back when both label and company are absent")
    void h1BodyFallsBackWhenBothLabelAndCompanyAbsent() {
        String message = copyWriter.interviewReminderMessage(null, null, "2026-06-18", ReminderOffset.H1);

        assertThat(message).isEqualTo("Your interview kicks off in about 1 hour (2026-06-18). "
                + "Grab some water, take a breath, you've got this.");
    }

    // CW-U-13
    @Test
    @DisplayName("CW-U-13: H24 body falls back when company is blank, not just null")
    void h24BodyFallsBackWhenCompanyBlank() {
        String message = copyWriter.interviewReminderMessage(
                "Interview with Product Manager", "   ", "2026-06-18", ReminderOffset.H24);

        assertThat(message).isEqualTo(copyWriter.interviewReminderMessage(
                "Interview with Product Manager", null, "2026-06-18", ReminderOffset.H24));
    }

    // CW-U-14
    @Test
    @DisplayName("CW-U-14: H1 body falls back when label is blank, not just null")
    void h1BodyFallsBackWhenLabelBlank() {
        String message = copyWriter.interviewReminderMessage(
                "   ", "Acme Corp", "2026-06-18", ReminderOffset.H1);

        assertThat(message).isEqualTo(copyWriter.interviewReminderMessage(
                null, "Acme Corp", "2026-06-18", ReminderOffset.H1));
    }

    // CW-U-15
    @Test
    @DisplayName("CW-U-15: security title is the fixed playful string, no placeholders")
    void securityTitleIsFixedPlayfulStringNoPlaceholders() {
        assertThat(copyWriter.securityRecommendationTitle()).isEqualTo("🛡️ Level up your account security!");
    }

    // CW-U-16
    @Test
    @DisplayName("CW-U-16: security body is the fixed playful string, no placeholders")
    void securityBodyIsFixedPlayfulStringNoPlaceholders() {
        assertThat(copyWriter.securityRecommendationMessage()).isEqualTo(
                "Two-factor authentication adds a second lock to your account, so a stolen password alone "
                        + "can't get anyone in. It takes about two minutes to set up in Settings, and "
                        + "future-you will thank present-you.");
    }

    // CW-U-17
    @Test
    @DisplayName("CW-U-17: security copy is byte-identical across repeated calls")
    void securityCopyIsByteIdenticalAcrossRepeatedCalls() {
        assertThat(copyWriter.securityRecommendationTitle()).isEqualTo(copyWriter.securityRecommendationTitle());
        assertThat(copyWriter.securityRecommendationMessage()).isEqualTo(copyWriter.securityRecommendationMessage());
    }
}
