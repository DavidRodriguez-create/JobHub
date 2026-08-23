package com.davidcreate.jobhub.notification.component_tests;

import com.davidcreate.jobhub.notification.adapter.out.mail.QuteDigestMailer;
import com.davidcreate.jobhub.notification.domain.model.DigestJob;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TC-200..TC-208: Verify that the restyled digest-email.html matches the architect's
 * palette and structural requirements (inline palette, Geist font, hairline card,
 * CTA button, footer).
 */
@QuarkusTest
@DisplayName("QuteDigestMailer Style Component Tests")
class QuteDigestMailerStyleComponentTest {

    @Inject
    QuteDigestMailer mailer;

    // TC-200: body background is #FAFAF7
    @Test
    @DisplayName("TC-200: digest_email_body_background_is_fafaf7")
    void digestEmailBodyBackgroundIsFafaf7() {
        String html = renderSingleJob();
        assertThat(html).contains("#FAFAF7");
    }

    // TC-201: card surface is #FFFFFF
    @Test
    @DisplayName("TC-201: digest_email_card_surface_is_ffffff")
    void digestEmailCardSurfaceIsFFFFFF() {
        String html = renderSingleJob();
        assertThat(html).contains("background-color:#FFFFFF")
                .overridingErrorMessage("Card surface must be #FFFFFF (inline)");
    }

    // TC-202: brand primary colour #2950E3 present (CTA button or links)
    @Test
    @DisplayName("TC-202: digest_email_brand_primary_colour_2950e3_is_present")
    void digestEmailBrandPrimaryColour2950e3IsPresent() {
        String html = renderSingleJob();
        assertThat(html).containsIgnoringCase("#2950E3");
    }

    // TC-203: ink primary colour #0B1220 present (heading text)
    @Test
    @DisplayName("TC-203: digest_email_ink_primary_colour_0b1220_is_present")
    void digestEmailInkPrimaryColour0b1220IsPresent() {
        String html = renderSingleJob();
        assertThat(html).containsIgnoringCase("#0B1220");
    }

    // TC-204: hairline colour #E6E6DE present (border or rule)
    @Test
    @DisplayName("TC-204: digest_email_hairline_colour_e6e6de_is_present")
    void digestEmailHairlineColourE6e6deIsPresent() {
        String html = renderSingleJob();
        assertThat(html).containsIgnoringCase("#E6E6DE");
    }

    // TC-205: Geist font stack is inlined in the body or wrapper element
    @Test
    @DisplayName("TC-205: digest_email_geist_font_stack_is_inlined")
    void digestEmailGeistFontStackIsInlined() {
        String html = renderSingleJob();
        assertThat(html).contains("Geist");
        assertThat(html).doesNotContain("@import");
    }

    // TC-206: CTA anchor links to {jobhubBaseUrl}/jobs/{id}
    @Test
    @DisplayName("TC-206: digest_email_cta_anchor_links_to_jobhub_jobs_url")
    void digestEmailCtaAnchorLinksToJobhubJobsUrl() {
        UUID jobId = UUID.randomUUID();
        DigestJob job = DigestJob.builder()
                .id(jobId)
                .title("Software Engineer")
                .companyName("Acme Corp")
                .location("Remote")
                .companyLogoUrl(null)
                .build();
        String html = mailer.render(List.of(job), true);
        assertThat(html).contains("/jobs/" + jobId);
    }

    // TC-207: border-radius 8px or 12px present on cards
    @Test
    @DisplayName("TC-207: digest_email_card_border_radius_8_or_12_px_present")
    void digestEmailCardBorderRadius8Or12PxPresent() {
        String html = renderSingleJob();
        assertThat(html).containsAnyOf("border-radius:8px", "border-radius:12px",
                "border-radius: 8px", "border-radius: 12px");
    }

    // TC-208: footer contains manage-preferences link to /settings/notifications
    @Test
    @DisplayName("TC-208: digest_email_footer_contains_manage_preferences_link")
    void digestEmailFooterContainsManagePreferencesLink() {
        String html = renderSingleJob();
        assertThat(html).contains("/settings/notifications");
    }

    private String renderSingleJob() {
        DigestJob job = DigestJob.builder()
                .id(UUID.randomUUID())
                .title("Backend Engineer")
                .companyName("Acme Corp")
                .location("Barcelona, Spain")
                .companyLogoUrl(URI.create("https://example.com/logo.png"))
                .build();
        return mailer.render(List.of(job), true);
    }
}
