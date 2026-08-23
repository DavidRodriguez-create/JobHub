package com.davidcreate.jobhub.job.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.job.adapter.out.persistence.entity.TriggerRequestEntity;
import com.davidcreate.jobhub.job.adapter.out.persistence.mapper.TriggerRequestMapper;
import com.davidcreate.jobhub.job.domain.model.TriggerKind;
import com.davidcreate.jobhub.job.domain.model.TriggerOrigin;
import com.davidcreate.jobhub.job.domain.model.TriggerOutcome;
import com.davidcreate.jobhub.job.domain.model.TriggerRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TriggerRequestMapper Unit Tests")
class TriggerRequestMapperTest {

    private final TriggerRequestMapper mapper = new TriggerRequestMapper();

    private static TriggerRequestEntity baseEntity() {
        TriggerRequestEntity e = new TriggerRequestEntity();
        e.id = UUID.randomUUID();
        e.kind = TriggerKind.CRAWL.value();
        e.status = "running";
        e.requestedAt = OffsetDateTime.now().minusMinutes(5);
        e.startedAt = OffsetDateTime.now().minusMinutes(4);
        return e;
    }

    @Test
    @DisplayName("TC-513-J1: toDomain builds a non-null TriggerProgress when progressUpdatedAt is set, all nine fields pass through unchanged")
    void toDomainBuildsProgressWhenUpdatedAtSet() {
        TriggerRequestEntity e = baseEntity();
        e.progressTargetsVisited = 3;
        e.progressNewPosts = 47;
        e.progressCurrentCompany = "Klaviyo";
        e.progressCurrentSourceType = "greenhouse";
        e.progressLastCompany = "Stripe";
        e.progressLastSourceType = "lever";
        e.progressLastFoundPosts = 142;
        e.progressLastNewPosts = 16;
        e.progressUpdatedAt = OffsetDateTime.now();

        TriggerRequest request = mapper.toDomain(e);

        assertThat(request.getProgress()).isNotNull();
        assertThat(request.getProgress().getTargetsVisited()).isEqualTo(3);
        assertThat(request.getProgress().getNewPosts()).isEqualTo(47);
        assertThat(request.getProgress().getCurrentCompany()).isEqualTo("Klaviyo");
        assertThat(request.getProgress().getCurrentSourceType()).isEqualTo("greenhouse");
        assertThat(request.getProgress().getLastCompany()).isEqualTo("Stripe");
        assertThat(request.getProgress().getLastSourceType()).isEqualTo("lever");
        assertThat(request.getProgress().getLastFoundPosts()).isEqualTo(142);
        assertThat(request.getProgress().getLastNewPosts()).isEqualTo(16);
        assertThat(request.getProgress().getUpdatedAt()).isEqualTo(e.progressUpdatedAt);
    }

    @Test
    @DisplayName("TC-513-J2: toDomain returns progress = null when progressUpdatedAt is null, even if other progress columns are non-null")
    void toDomainReturnsNullProgressWhenUpdatedAtNull() {
        TriggerRequestEntity e = baseEntity();
        e.progressTargetsVisited = 0;
        e.progressUpdatedAt = null;

        TriggerRequest request = mapper.toDomain(e);

        assertThat(request.getProgress()).isNull();
    }

    @Test
    @DisplayName("C28: toDomain defaults origin to MANUAL and leaves outcome null when the row predates ADR 0032 (columns null), no NPE")
    void toDomainDefaultsOriginToManualWhenColumnsNull() {
        TriggerRequestEntity e = baseEntity();
        e.origin = null;
        e.outcome = null;

        TriggerRequest request = mapper.toDomain(e);

        assertThat(request.getOrigin()).isEqualTo(TriggerOrigin.MANUAL);
        assertThat(request.getOutcome()).isNull();
    }

    @Test
    @DisplayName("toDomain maps populated origin/outcome columns through unchanged")
    void toDomainMapsOriginAndOutcomeWhenPresent() {
        TriggerRequestEntity e = baseEntity();
        e.origin = "scheduled";
        e.outcome = "no_targets";

        TriggerRequest request = mapper.toDomain(e);

        assertThat(request.getOrigin()).isEqualTo(TriggerOrigin.SCHEDULED);
        assertThat(request.getOutcome()).isEqualTo(TriggerOutcome.NO_TARGETS);
    }
}
