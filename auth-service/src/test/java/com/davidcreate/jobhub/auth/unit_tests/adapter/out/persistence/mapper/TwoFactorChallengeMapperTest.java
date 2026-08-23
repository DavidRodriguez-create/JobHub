package com.davidcreate.jobhub.auth.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.TwoFactorChallengeEntity;
import com.davidcreate.jobhub.auth.adapter.out.persistence.mapper.TwoFactorChallengeMapper;
import com.davidcreate.jobhub.auth.domain.entity.TwoFactorChallenge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TwoFactorChallengeMapper Unit Tests")
class TwoFactorChallengeMapperTest {

    TwoFactorChallengeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TwoFactorChallengeMapper();
    }

    @Test
    @DisplayName("TC-MAP-02: domain -> entity -> domain round-trip preserves all fields")
    void roundTripPreservesFields() {
        TwoFactorChallenge original = TwoFactorChallenge.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash("hash-value")
                .expiresAt(OffsetDateTime.now().plusMinutes(5))
                .consumedAt(null)
                .createdAt(OffsetDateTime.now())
                .build();

        TwoFactorChallengeEntity entity = mapper.toEntity(original);
        TwoFactorChallenge roundTripped = mapper.toDomain(entity);

        assertThat(roundTripped.getId()).isEqualTo(original.getId());
        assertThat(roundTripped.getUserId()).isEqualTo(original.getUserId());
        assertThat(roundTripped.getTokenHash()).isEqualTo(original.getTokenHash());
        assertThat(roundTripped.getExpiresAt()).isEqualTo(original.getExpiresAt());
        assertThat(roundTripped.getConsumedAt()).isNull();
        assertThat(roundTripped.getCreatedAt()).isEqualTo(original.getCreatedAt());
    }

    @Test
    @DisplayName("toDomain copies consumedAt when set")
    void toDomainCopiesConsumedAt() {
        TwoFactorChallengeEntity e = new TwoFactorChallengeEntity();
        e.id = UUID.randomUUID();
        e.userId = UUID.randomUUID();
        e.tokenHash = "h";
        e.expiresAt = OffsetDateTime.now().plusMinutes(5);
        e.consumedAt = OffsetDateTime.now();
        e.createdAt = OffsetDateTime.now();

        TwoFactorChallenge d = mapper.toDomain(e);

        assertThat(d.getConsumedAt()).isEqualTo(e.consumedAt);
    }
}
