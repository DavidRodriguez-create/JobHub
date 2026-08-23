package com.davidcreate.jobhub.auth.unit_tests.domain.entity;

import com.davidcreate.jobhub.auth.domain.entity.TwoFactorChallenge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TwoFactorChallenge Unit Tests — TC-DOM-02..04")
class TwoFactorChallengeTest {

    private TwoFactorChallenge.TwoFactorChallengeBuilder base() {
        return TwoFactorChallenge.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .tokenHash("hash");
    }

    @Test
    @DisplayName("TC-DOM-02: isUsable(now) returns false when expired")
    void expiredIsNotUsable() {
        TwoFactorChallenge challenge = base()
                .expiresAt(OffsetDateTime.now().minusMinutes(1))
                .build();

        assertThat(challenge.isUsable(OffsetDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("TC-DOM-03: isUsable(now) returns false when consumed")
    void consumedIsNotUsable() {
        TwoFactorChallenge challenge = base()
                .expiresAt(OffsetDateTime.now().plusMinutes(5))
                .consumedAt(OffsetDateTime.now())
                .build();

        assertThat(challenge.isUsable(OffsetDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("TC-DOM-04: isUsable(now) returns true when unexpired and unconsumed")
    void validIsUsable() {
        TwoFactorChallenge challenge = base()
                .expiresAt(OffsetDateTime.now().plusMinutes(5))
                .build();

        assertThat(challenge.isUsable(OffsetDateTime.now())).isTrue();
    }
}
