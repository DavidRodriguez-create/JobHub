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

@QuarkusTest
@DisplayName("QuteDigestMailer Component Tests")
class QuteDigestMailerComponentTest {

    @Inject
    QuteDigestMailer mailer;

    // TC-16
    @Test
    @DisplayName("rendered_digest_email_job_card_contains_title_company_location_and_jobhub_link")
    void renderedJobCardContainsTitleCompanyLocationAndJobHubLink() {
        UUID jobId = UUID.randomUUID();
        DigestJob job = DigestJob.builder()
                .id(jobId)
                .title("Backend Engineer")
                .companyName("Acme Corp")
                .location("Barcelona, Spain")
                .companyLogoUrl(null)
                .build();

        String html = mailer.render(List.of(job), true);

        assertThat(html).contains("Backend Engineer");
        assertThat(html).contains("Acme Corp");
        assertThat(html).contains("Barcelona, Spain");
        assertThat(html).contains("/jobs/" + jobId);
        assertThat(html).doesNotContain("indeed.com").doesNotContain("linkedin.com").doesNotContain("glassdoor.com");
        assertThat(html).isNotBlank();
    }

    // TC-17
    @Test
    @DisplayName("rendered_digest_email_distinguishes_personalised_vs_generic_framing")
    void renderedEmailDistinguishesPersonalisedVsGenericFraming() {
        DigestJob job = DigestJob.builder()
                .id(UUID.randomUUID())
                .title("Backend Engineer")
                .companyName("Acme Corp")
                .location("Barcelona, Spain")
                .companyLogoUrl(URI.create("https://example.com/logo.png"))
                .build();

        String personalisedHtml = mailer.render(List.of(job), true);
        String genericHtml = mailer.render(List.of(job), false);

        String personalisedHeader = extractHeader(personalisedHtml);
        String genericHeader = extractHeader(genericHtml);

        assertThat(personalisedHeader.toLowerCase()).containsAnyOf("interest", "matching");
        assertThat(genericHeader.toLowerCase()).doesNotContain("interest").doesNotContain("matching");
        assertThat(genericHeader.toLowerCase()).contains("top jobs");

        assertThat(personalisedHeader).isNotEqualTo(genericHeader);
    }

    // TC-33
    @Test
    @DisplayName("rendered_digest_email_footer_contains_unsubscribe_hint")
    void renderedEmailFooterContainsUnsubscribeHint() {
        DigestJob job = DigestJob.builder()
                .id(UUID.randomUUID())
                .title("Backend Engineer")
                .companyName("Acme Corp")
                .location("Barcelona, Spain")
                .companyLogoUrl(null)
                .build();

        String html = mailer.render(List.of(job), true);
        String lowerHtml = html.toLowerCase();

        assertThat(lowerHtml).contains("jobhub");
        assertThat(lowerHtml).containsAnyOf("copyright", "&copy;", "©");
        assertThat(lowerHtml).contains("settings");
        assertThat(lowerHtml).contains("notifications");
        assertThat(lowerHtml).contains("weekly digest email");
    }

    // DG-C-26
    @Test
    @DisplayName("DG-C-26: header tone line appears before the existing framing line")
    void headerToneLineAppearsBeforeFramingLine() {
        DigestJob job = DigestJob.builder()
                .id(UUID.randomUUID())
                .title("Backend Engineer")
                .companyName("Acme Corp")
                .location("Barcelona, Spain")
                .companyLogoUrl(null)
                .build();

        String html = mailer.render(List.of(job), true);
        String header = extractHeader(html);

        int toneIndex = header.indexOf("Fresh off the crawler, just for you 🎯");
        int framingIndex = header.indexOf("Jobs matching your interests this week");

        assertThat(toneIndex).isGreaterThanOrEqualTo(0);
        assertThat(framingIndex).isGreaterThanOrEqualTo(0);
        assertThat(toneIndex).isLessThan(framingIndex);
    }

    // DG-C-27
    @Test
    @DisplayName("DG-C-27: footer sign-off appears immediately before the unsubscribe hint")
    void footerSignOffAppearsImmediatelyBeforeUnsubscribeHint() {
        DigestJob job = DigestJob.builder()
                .id(UUID.randomUUID())
                .title("Backend Engineer")
                .companyName("Acme Corp")
                .location("Barcelona, Spain")
                .companyLogoUrl(null)
                .build();

        String html = mailer.render(List.of(job), true);

        int signOffIndex = html.indexOf("Happy hunting! The JobHub team");
        int unsubscribeIndex = html.indexOf("You're receiving this email because");

        assertThat(signOffIndex).isGreaterThanOrEqualTo(0);
        assertThat(unsubscribeIndex).isGreaterThanOrEqualTo(0);
        assertThat(signOffIndex).isLessThan(unsubscribeIndex);

        String between = html.substring(signOffIndex + "Happy hunting! The JobHub team".length(), unsubscribeIndex);
        assertThat(between.toLowerCase()).doesNotContain("href");
        assertThat(between.toLowerCase().split("<p", -1).length - 1).isEqualTo(1);
    }

    // DG-C-27 (generic flow): the PDA spec says both the personalised and generic flows
    // carry the footer tone line, since the template applies it unconditionally
    // (not gated on isPersonalised). Re-runs the same assertion as the personalised case
    // above with isPersonalised=false.
    @Test
    @DisplayName("DG-C-27: footer sign-off appears immediately before the unsubscribe hint in the generic flow too")
    void footerSignOffAppearsImmediatelyBeforeUnsubscribeHintForGenericFlow() {
        DigestJob job = DigestJob.builder()
                .id(UUID.randomUUID())
                .title("Backend Engineer")
                .companyName("Acme Corp")
                .location("Barcelona, Spain")
                .companyLogoUrl(null)
                .build();

        String html = mailer.render(List.of(job), false);

        int signOffIndex = html.indexOf("Happy hunting! The JobHub team");
        int unsubscribeIndex = html.indexOf("You're receiving this email because");

        assertThat(signOffIndex).isGreaterThanOrEqualTo(0);
        assertThat(unsubscribeIndex).isGreaterThanOrEqualTo(0);
        assertThat(signOffIndex).isLessThan(unsubscribeIndex);

        String between = html.substring(signOffIndex + "Happy hunting! The JobHub team".length(), unsubscribeIndex);
        assertThat(between.toLowerCase()).doesNotContain("href");
        assertThat(between.toLowerCase().split("<p", -1).length - 1).isEqualTo(1);
    }

    // DG-C-28: regression guard, re-running TC-16/17/33 to confirm no regression from the
    // tone additions (same assertions as TC-16/TC-17/TC-33, kept here as an explicit
    // post-tone-change confirmation rather than duplicated logic with weaker checks)
    @Test
    @DisplayName("DG-C-28: tone additions do not regress job-card content, framing distinction, or unsubscribe hint")
    void toneAdditionsDoNotRegressExistingContent() {
        UUID jobId = UUID.randomUUID();
        DigestJob job = DigestJob.builder()
                .id(jobId)
                .title("Backend Engineer")
                .companyName("Acme Corp")
                .location("Barcelona, Spain")
                .companyLogoUrl(null)
                .build();

        String personalisedHtml = mailer.render(List.of(job), true);
        String genericHtml = mailer.render(List.of(job), false);

        // TC-16 guard
        assertThat(personalisedHtml).contains("Backend Engineer");
        assertThat(personalisedHtml).contains("Acme Corp");
        assertThat(personalisedHtml).contains("Barcelona, Spain");
        assertThat(personalisedHtml).contains("/jobs/" + jobId);

        // TC-17 guard
        String personalisedHeader = extractHeader(personalisedHtml);
        String genericHeader = extractHeader(genericHtml);
        assertThat(personalisedHeader.toLowerCase()).containsAnyOf("interest", "matching");
        assertThat(genericHeader.toLowerCase()).doesNotContain("interest").doesNotContain("matching");
        assertThat(genericHeader.toLowerCase()).contains("top jobs");
        assertThat(personalisedHeader).isNotEqualTo(genericHeader);

        // TC-33 guard
        String lowerHtml = personalisedHtml.toLowerCase();
        assertThat(lowerHtml).contains("jobhub");
        assertThat(lowerHtml).containsAnyOf("copyright", "&copy;", "©");
        assertThat(lowerHtml).contains("settings");
        assertThat(lowerHtml).contains("notifications");
        assertThat(lowerHtml).contains("weekly digest email");
    }

    private String extractHeader(String html) {
        int start = html.indexOf("<header");
        int end = html.indexOf("</header>");
        return html.substring(start, end + "</header>".length());
    }
}
