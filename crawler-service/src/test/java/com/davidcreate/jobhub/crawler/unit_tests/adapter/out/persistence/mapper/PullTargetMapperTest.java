package com.davidcreate.jobhub.crawler.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.crawler.adapter.out.persistence.entity.PullTargetEntity;
import com.davidcreate.jobhub.crawler.adapter.out.persistence.mapper.PullTargetMapper;
import com.davidcreate.jobhub.crawler.domain.model.PullTarget;
import com.davidcreate.jobhub.crawler.domain.model.PullTargetStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PullTargetMapper Unit Tests")
class PullTargetMapperTest {

    PullTargetMapper mapper;

    private static final UUID ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-000000000001");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2024-06-01T08:00:00Z");

    @BeforeEach
    void setUp() {
        mapper = new PullTargetMapper();
    }

    @Test
    @DisplayName("toDomain maps all fields and upcases status string to enum")
    void toDomainMapsAllFields() {
        PullTargetEntity entity = buildEntity("active");

        PullTarget domain = mapper.toDomain(entity);

        assertThat(domain.getId()).isEqualTo(ID);
        assertThat(domain.getSourceType()).isEqualTo("greenhouse");
        assertThat(domain.getCompanyName()).isEqualTo("TestCo");
        assertThat(domain.getToken()).isEqualTo("testco");
        assertThat(domain.getScraperConfig()).isEqualTo("{\"key\":\"val\"}");
        assertThat(domain.getPullPriority()).isEqualTo((short) 90);
        assertThat(domain.getStatus()).isEqualTo(PullTargetStatus.ACTIVE);
        assertThat(domain.getStatusReason()).isEqualTo("reason");
        assertThat(domain.getStatusChangedAt()).isEqualTo(NOW);
        assertThat(domain.getLockedBy()).isEqualTo("worker-1");
        assertThat(domain.getLeaseExpiresAt()).isEqualTo(NOW.plusMinutes(30));
        assertThat(domain.getLastSuccessfulPull()).isEqualTo(NOW.minusHours(1));
        assertThat(domain.getLastPullAttempt()).isEqualTo(NOW.minusMinutes(5));
        assertThat(domain.getConsecutiveFailures()).isEqualTo((short) 2);
        assertThat(domain.getCreatedAt()).isEqualTo(NOW.minusDays(10));
        assertThat(domain.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("toDomain handles all PullTargetStatus values")
    void toDomainHandlesAllStatuses() {
        assertThat(mapper.toDomain(buildEntity("active")).getStatus()).isEqualTo(PullTargetStatus.ACTIVE);
        assertThat(mapper.toDomain(buildEntity("cooldown")).getStatus()).isEqualTo(PullTargetStatus.COOLDOWN);
        assertThat(mapper.toDomain(buildEntity("disabled_transient")).getStatus()).isEqualTo(PullTargetStatus.DISABLED_TRANSIENT);
        assertThat(mapper.toDomain(buildEntity("disabled_permanent")).getStatus()).isEqualTo(PullTargetStatus.DISABLED_PERMANENT);
    }

    @Test
    @DisplayName("toDomain preserves null optional fields")
    void toDomainPreservesNulls() {
        PullTargetEntity entity = new PullTargetEntity();
        entity.id = ID;
        entity.sourceType = "lever";
        entity.companyName = "NullCo";
        entity.token = null;
        entity.scraperConfig = null;
        entity.pullPriority = 100;
        entity.status = "active";
        entity.statusReason = null;
        entity.statusChangedAt = NOW;
        entity.lockedBy = null;
        entity.leaseExpiresAt = null;
        entity.lastSuccessfulPull = null;
        entity.lastPullAttempt = null;
        entity.consecutiveFailures = 0;
        entity.createdAt = NOW;
        entity.updatedAt = NOW;

        PullTarget domain = mapper.toDomain(entity);

        assertThat(domain.getToken()).isNull();
        assertThat(domain.getScraperConfig()).isNull();
        assertThat(domain.getStatusReason()).isNull();
        assertThat(domain.getLockedBy()).isNull();
        assertThat(domain.getLeaseExpiresAt()).isNull();
        assertThat(domain.getLastSuccessfulPull()).isNull();
        assertThat(domain.getLastPullAttempt()).isNull();
    }

    @Test
    @DisplayName("toEntity lowercases status enum to string")
    void toEntityLowercasesStatus() {
        PullTarget domain = PullTarget.builder()
                .id(ID)
                .sourceType("greenhouse")
                .companyName("TestCo")
                .status(PullTargetStatus.COOLDOWN)
                .nextPullAfter(NOW)
                .createdAt(NOW)
                .updatedAt(NOW)
                .statusChangedAt(NOW)
                .build();

        PullTargetEntity entity = mapper.toEntity(domain);

        assertThat(entity.status).isEqualTo("cooldown");
    }

    @Test
    @DisplayName("toEntity maps all fields from domain")
    void toEntityMapsAllFields() {
        PullTarget domain = PullTarget.builder()
                .id(ID)
                .sourceType("greenhouse")
                .companyName("TestCo")
                .token("testco")
                .scraperConfig("{\"key\":\"val\"}")
                .pullPriority((short) 90)
                .nextPullAfter(NOW.plusHours(1))
                .status(PullTargetStatus.ACTIVE)
                .statusReason("ok")
                .statusChangedAt(NOW)
                .lockedBy("worker-1")
                .leaseExpiresAt(NOW.plusMinutes(30))
                .lastSuccessfulPull(NOW.minusHours(1))
                .lastPullAttempt(NOW.minusMinutes(5))
                .consecutiveFailures((short) 0)
                .createdAt(NOW.minusDays(10))
                .updatedAt(NOW)
                .build();

        PullTargetEntity entity = mapper.toEntity(domain);

        assertThat(entity.id).isEqualTo(ID);
        assertThat(entity.sourceType).isEqualTo("greenhouse");
        assertThat(entity.companyName).isEqualTo("TestCo");
        assertThat(entity.token).isEqualTo("testco");
        assertThat(entity.scraperConfig).isEqualTo("{\"key\":\"val\"}");
        assertThat(entity.pullPriority).isEqualTo((short) 90);
        assertThat(entity.nextPullAfter).isEqualTo(NOW.plusHours(1));
        assertThat(entity.status).isEqualTo("active");
        assertThat(entity.statusReason).isEqualTo("ok");
        assertThat(entity.statusChangedAt).isEqualTo(NOW);
        assertThat(entity.lockedBy).isEqualTo("worker-1");
        assertThat(entity.leaseExpiresAt).isEqualTo(NOW.plusMinutes(30));
        assertThat(entity.lastSuccessfulPull).isEqualTo(NOW.minusHours(1));
        assertThat(entity.lastPullAttempt).isEqualTo(NOW.minusMinutes(5));
        assertThat(entity.consecutiveFailures).isEqualTo((short) 0);
        assertThat(entity.createdAt).isEqualTo(NOW.minusDays(10));
        assertThat(entity.updatedAt).isEqualTo(NOW);
    }

    @Test
    @DisplayName("updateEntity patches only mutable state fields")
    void updateEntityPatchesMutableFields() {
        PullTargetEntity entity = buildEntity("active");
        String originalCompany = entity.companyName;

        PullTarget domain = PullTarget.builder()
                .id(ID)
                .sourceType("greenhouse")
                .companyName("ShouldNotChange")
                .status(PullTargetStatus.COOLDOWN)
                .statusReason("rate limited")
                .statusChangedAt(NOW.plusHours(1))
                .lockedBy(null)
                .leaseExpiresAt(null)
                .lastSuccessfulPull(NOW)
                .lastPullAttempt(NOW)
                .consecutiveFailures((short) 3)
                .nextPullAfter(NOW.plusMinutes(30))
                .updatedAt(NOW.plusSeconds(1))
                .build();

        mapper.updateEntity(entity, domain);

        assertThat(entity.status).isEqualTo("cooldown");
        assertThat(entity.statusReason).isEqualTo("rate limited");
        assertThat(entity.lockedBy).isNull();
        assertThat(entity.consecutiveFailures).isEqualTo((short) 3);
        assertThat(entity.nextPullAfter).isEqualTo(NOW.plusMinutes(30));
        assertThat(entity.companyName).isEqualTo(originalCompany);
    }

    private PullTargetEntity buildEntity(String status) {
        PullTargetEntity entity = new PullTargetEntity();
        entity.id = ID;
        entity.sourceType = "greenhouse";
        entity.companyName = "TestCo";
        entity.token = "testco";
        entity.scraperConfig = "{\"key\":\"val\"}";
        entity.pullPriority = 90;
        entity.nextPullAfter = NOW.plusHours(1);
        entity.status = status;
        entity.statusReason = "reason";
        entity.statusChangedAt = NOW;
        entity.lockedBy = "worker-1";
        entity.leaseExpiresAt = NOW.plusMinutes(30);
        entity.lastSuccessfulPull = NOW.minusHours(1);
        entity.lastPullAttempt = NOW.minusMinutes(5);
        entity.consecutiveFailures = 2;
        entity.createdAt = NOW.minusDays(10);
        entity.updatedAt = NOW;
        return entity;
    }
}
