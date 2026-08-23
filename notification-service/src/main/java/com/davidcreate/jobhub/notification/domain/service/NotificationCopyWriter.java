package com.davidcreate.jobhub.notification.domain.service;

import com.davidcreate.jobhub.notification.domain.model.ReminderOffset;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Centralises the playful, branded title/message copy for the automatically-minted
 * notification types (GHOSTED_ALERT, INTERVIEW_REMINDER, SECURITY_RECOMMENDATION). Plain
 * domain logic, no IO: emoji, interpolation, and graceful fallbacks for missing data all
 * live here so the minting services only orchestrate, never build strings.
 */
@ApplicationScoped
public class NotificationCopyWriter {

    private static final String GHOSTED_ALERT_TITLE = "👻 A wild ghost appeared!";
    private static final String INTERVIEW_REMINDER_H24_TITLE = "⏰ Countdown to showtime!";
    private static final String INTERVIEW_REMINDER_H1_TITLE = "🎤 You're up soon!";
    private static final String SECURITY_RECOMMENDATION_TITLE = "🛡️ Level up your account security!";
    private static final String SECURITY_RECOMMENDATION_MESSAGE =
            "Two-factor authentication adds a second lock to your account, so a stolen password alone "
                    + "can't get anyone in. It takes about two minutes to set up in Settings, and "
                    + "future-you will thank present-you.";
    private static final String GENERIC_INTERVIEW_LABEL = "interview";

    public String ghostedAlertTitle() {
        return GHOSTED_ALERT_TITLE;
    }

    public String ghostedAlertMessage(String jobTitle) {
        if (isBlank(jobTitle)) {
            return "Your application seems to have disappeared into the hiring void. If you're still "
                    + "interested in the position, a quick follow-up with the recruiter could bring it "
                    + "back to life. Don't give up! Your next opportunity might be just around the corner.";
        }
        return "Your application " + jobTitle.trim() + " seems to have disappeared into the hiring void. "
                + "If you're still interested in the position, a quick follow-up with the recruiter could "
                + "bring it back to life. Don't give up! Your next opportunity might be just around the corner.";
    }

    public String interviewReminderTitle(ReminderOffset offset) {
        return offset == ReminderOffset.H24 ? INTERVIEW_REMINDER_H24_TITLE : INTERVIEW_REMINDER_H1_TITLE;
    }

    public String interviewReminderMessage(String label, String company, String stepDate, ReminderOffset offset) {
        String subject = buildSubject(label, company);
        if (offset == ReminderOffset.H24) {
            return "Your " + subject + " is coming up in about 24 hours (" + stepDate + "). Take a breath, "
                    + "review your notes, and go show them what you've got.";
        }
        return "Your " + subject + " kicks off in about 1 hour (" + stepDate + "). Grab some water, "
                + "take a breath, you've got this.";
    }

    private String buildSubject(String label, String company) {
        String effectiveLabel = isBlank(label) ? GENERIC_INTERVIEW_LABEL : label.trim();
        if (isBlank(company)) {
            return effectiveLabel;
        }
        return effectiveLabel + " with " + company.trim();
    }

    public String securityRecommendationTitle() {
        return SECURITY_RECOMMENDATION_TITLE;
    }

    public String securityRecommendationMessage() {
        return SECURITY_RECOMMENDATION_MESSAGE;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
