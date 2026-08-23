package com.davidcreate.jobhub.notification.component_tests;

import com.davidcreate.jobhub.notification.adapter.out.mail.QuteAlertMailer;
import com.davidcreate.jobhub.notification.domain.model.StaleApplication;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-218..TC-223: Verify the new ghosted-alert-email.html template is used by
 * QuteAlertMailer.render(StaleApplication) and matches the architect's palette.
 *
 * Prerequisites per QAE spec:
 *  - QuteAlertMailer exposes a public render(StaleApplication) -> String method
 *  - The mailer uses Mail.withHtml(...) (not Mail.withText)
 *  - ghosted-alert-email.html Qute template exists
 *  - jobhubBaseUrl is injected
 */
@QuarkusTest
@DisplayName("QuteAlertMailer Style Component Tests")
class QuteAlertMailerStyleComponentTest {

    @Inject
    QuteAlertMailer mailer;

    // TC-218: render method exists and returns non-blank HTML
    @Test
    @DisplayName("TC-218: alert_mailer_render_returns_non_blank_html")
    void alertMailerRenderReturnsNonBlankHtml() {
        StaleApplication app = buildApp("Acme Corp", 14);
        String html = mailer.render(app);
        assertThat(html).isNotBlank();
        assertThat(html.trim()).startsWith("<");
    }

    // TC-219: body background is #FAFAF7
    @Test
    @DisplayName("TC-219: alert_email_body_background_is_fafaf7")
    void alertEmailBodyBackgroundIsFafaf7() {
        String html = mailer.render(buildApp("Acme Corp", 14));
        assertThat(html).contains("#FAFAF7");
    }

    // TC-220: brand primary colour #2950E3 present
    @Test
    @DisplayName("TC-220: alert_email_brand_primary_colour_2950e3_is_present")
    void alertEmailBrandPrimaryColour2950e3IsPresent() {
        String html = mailer.render(buildApp("Acme Corp", 14));
        assertThat(html).containsIgnoringCase("#2950E3");
    }

    // TC-221: Geist font stack inlined, no @import
    @Test
    @DisplayName("TC-221: alert_email_geist_font_stack_inlined_no_import")
    void alertEmailGeistFontStackInlinedNoImport() {
        String html = mailer.render(buildApp("Acme Corp", 14));
        assertThat(html).contains("Geist");
        assertThat(html).doesNotContain("@import");
    }

    // TC-222: company name and days-since-last-activity are rendered
    @Test
    @DisplayName("TC-222: alert_email_renders_company_and_days_since_activity")
    void alertEmailRendersCompanyAndDaysSinceActivity() {
        StaleApplication app = buildApp("Globex Corporation", 21);
        String html = mailer.render(app);
        assertThat(html).contains("Globex Corporation");
        assertThat(html).contains("21");
    }

    // TC-223: hairline colour #E6E6DE present and footer with manage-preferences link
    @Test
    @DisplayName("TC-223: alert_email_hairline_present_and_footer_manage_preferences_link")
    void alertEmailHairlinePresentAndFooterManagePreferencesLink() {
        String html = mailer.render(buildApp("Acme Corp", 14));
        assertThat(html).containsIgnoringCase("#E6E6DE");
        assertThat(html).contains("/settings/notifications");
    }

    private StaleApplication buildApp(String company, int daysSince) {
        return StaleApplication.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .jobTitle("Software Engineer")
                .company(company)
                .daysSinceLastActivity(daysSince)
                .build();
    }
}
