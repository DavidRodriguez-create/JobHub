package com.davidcreate.jobhub.job.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.job.adapter.out.persistence.entity.CompanyEntity;
import com.davidcreate.jobhub.job.adapter.out.persistence.mapper.CompanyMapper;
import com.davidcreate.jobhub.job.domain.model.Company;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CompanyMapper Unit Tests")
class CompanyMapperTest {

    private CompanyMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new CompanyMapper();
    }

    // ── QAE-428-CMAP-01 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-428-CMAP-01: a fully-populated CompanyEntity maps 1:1 to Company, "
            + "including tags order preserved")
    void fullyPopulatedEntityMapsOneToOne() {
        CompanyEntity entity = new CompanyEntity();
        entity.id = UUID.randomUUID();
        entity.slug = "stripe";
        entity.name = "Stripe";
        entity.website = "https://stripe.com";
        entity.industry = "Fintech";
        entity.size = "5001-10000";
        entity.headquarters = "San Francisco, United States";
        entity.description = "Financial infrastructure for the internet.";
        entity.logoUrl = "https://example.com/logos/stripe.png";
        entity.tags = List.of("remote-first", "b2b");
        entity.source = "crawl";
        entity.manuallyEdited = false;
        entity.createdAt = OffsetDateTime.parse("2026-05-01T00:00:00Z");
        entity.updatedAt = OffsetDateTime.parse("2026-06-01T00:00:00Z");

        Company domain = mapper.toDomain(entity);

        assertThat(domain.getId()).isEqualTo(entity.id);
        assertThat(domain.getSlug()).isEqualTo("stripe");
        assertThat(domain.getName()).isEqualTo("Stripe");
        assertThat(domain.getWebsite()).isEqualTo("https://stripe.com");
        assertThat(domain.getIndustry()).isEqualTo("Fintech");
        assertThat(domain.getSize()).isEqualTo("5001-10000");
        assertThat(domain.getHeadquarters()).isEqualTo("San Francisco, United States");
        assertThat(domain.getDescription()).isEqualTo("Financial infrastructure for the internet.");
        assertThat(domain.getLogoUrl()).isEqualTo("https://example.com/logos/stripe.png");
        assertThat(domain.getTags()).containsExactly("remote-first", "b2b");
        assertThat(domain.getManuallyEdited()).isFalse();
        assertThat(domain.getUpdatedAt()).isEqualTo(entity.updatedAt);
    }

    // ── QAE-428-CMAP-02 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-428-CMAP-02: tags column NULL maps to a null domain list, never an empty list")
    void nullTagsMapToNullNeverEmptyList() {
        CompanyEntity entity = new CompanyEntity();
        entity.id = UUID.randomUUID();
        entity.slug = "acme-only";
        entity.name = "Acme Only";
        entity.tags = null;
        entity.source = "crawl";
        entity.manuallyEdited = false;
        entity.updatedAt = OffsetDateTime.now();

        Company domain = mapper.toDomain(entity);

        assertThat(domain.getTags()).isNull();
    }

    // ── QAE-428-CMAP-03 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("QAE-428-CMAP-03: every nullable column left NULL maps to null; name always mapped")
    void nullableColumnsMapToNullNameAlwaysMapped() {
        CompanyEntity entity = new CompanyEntity();
        entity.id = UUID.randomUUID();
        entity.slug = "acme-only";
        entity.name = "Acme Only";
        entity.website = null;
        entity.industry = null;
        entity.size = null;
        entity.headquarters = null;
        entity.description = null;
        entity.logoUrl = null;
        entity.tags = null;
        entity.source = "crawl";
        entity.manuallyEdited = false;
        entity.updatedAt = OffsetDateTime.now();

        Company domain = mapper.toDomain(entity);

        assertThat(domain.getName()).isEqualTo("Acme Only");
        assertThat(domain.getWebsite()).isNull();
        assertThat(domain.getIndustry()).isNull();
        assertThat(domain.getSize()).isNull();
        assertThat(domain.getHeadquarters()).isNull();
        assertThat(domain.getDescription()).isNull();
        assertThat(domain.getLogoUrl()).isNull();
        assertThat(domain.getTags()).isNull();
    }

    @Test
    @DisplayName("QAE-428-CMAP-03: created_at is DB-internal bookkeeping - Company has no getCreatedAt() at all")
    void companyDomainModelHasNoCreatedAtAccessor() {
        assertThat(hasMethod(Company.class, "getCreatedAt"))
                .as("Company must not expose getCreatedAt() - the contract has no createdAt property")
                .isFalse();
    }

    private static boolean hasMethod(Class<?> type, String methodName) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }
}
