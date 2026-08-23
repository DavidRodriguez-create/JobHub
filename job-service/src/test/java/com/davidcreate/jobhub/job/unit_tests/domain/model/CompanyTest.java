package com.davidcreate.jobhub.job.unit_tests.domain.model;

import com.davidcreate.jobhub.job.domain.model.Company;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Company Domain Model Unit Tests")
class CompanyTest {

    // ── QAE-428-MODEL-01 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-428-MODEL-01: builder round-trip carries every one of the twelve fields unchanged")
    void builderRoundTripCarriesEveryField() {
        UUID id = UUID.randomUUID();
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-06-01T00:00:00Z");
        List<String> tags = List.of("remote-first", "b2b");

        Company company = Company.builder()
                .id(id)
                .slug("stripe")
                .name("Stripe")
                .website("https://stripe.com")
                .industry("Fintech")
                .size("5001-10000")
                .headquarters("San Francisco, United States")
                .description("Financial infrastructure for the internet.")
                .logoUrl("https://example.com/logos/stripe.png")
                .tags(tags)
                .manuallyEdited(true)
                .updatedAt(updatedAt)
                .build();

        assertThat(company.getId()).isEqualTo(id);
        assertThat(company.getSlug()).isEqualTo("stripe");
        assertThat(company.getName()).isEqualTo("Stripe");
        assertThat(company.getWebsite()).isEqualTo("https://stripe.com");
        assertThat(company.getIndustry()).isEqualTo("Fintech");
        assertThat(company.getSize()).isEqualTo("5001-10000");
        assertThat(company.getHeadquarters()).isEqualTo("San Francisco, United States");
        assertThat(company.getDescription()).isEqualTo("Financial infrastructure for the internet.");
        assertThat(company.getLogoUrl()).isEqualTo("https://example.com/logos/stripe.png");
        assertThat(company.getTags()).containsExactlyElementsOf(tags);
        assertThat(company.getManuallyEdited()).isTrue();
        assertThat(company.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("QAE-428-MODEL-01: manuallyEdited is a boxed Boolean - a builder that never sets it "
            + "produces null, not false (the unresolved-state signal, AC-428-13)")
    void manuallyEditedDefaultsToNullNotFalse() {
        Company company = Company.builder()
                .name("Northwind Freight")
                .build();

        assertThat(company.getManuallyEdited()).isNull();
    }
}
