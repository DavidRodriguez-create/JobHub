package com.davidcreate.jobhub.auth.unit_tests.domain.entity;

import com.davidcreate.jobhub.auth.domain.entity.VerificationCode;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EV-U-13: VerificationCode.isUsable — true for unconsumed+unexpired, false otherwise.
 */
@DisplayName("VerificationCode.isUsable — EV-U-13")
class VerificationCodeTest {

    private VerificationCode code(OffsetDateTime expiresAt, OffsetDateTime consumedAt) {
        return VerificationCode.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .action(VerificationAction.VERIFY_EMAIL)
                .codeHash("hash")
                .expiresAt(expiresAt)
                .consumedAt(consumedAt)
                .build();
    }

    @Test
    @DisplayName("EV-U-13a: unconsumed + not expired → isUsable true")
    void usableWhenUnconsumedAndNotExpired() {
        VerificationCode c = code(OffsetDateTime.now().plusMinutes(5), null);
        assertThat(c.isUsable(OffsetDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("EV-U-13b: expired → isUsable false")
    void notUsableWhenExpired() {
        VerificationCode c = code(OffsetDateTime.now().minusSeconds(1), null);
        assertThat(c.isUsable(OffsetDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("EV-U-13c: consumed → isUsable false")
    void notUsableWhenConsumed() {
        VerificationCode c = code(OffsetDateTime.now().plusMinutes(5), OffsetDateTime.now().minusMinutes(1));
        assertThat(c.isUsable(OffsetDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("EV-U-13d: null expiresAt → isUsable false (guard)")
    void notUsableWhenNullExpiry() {
        VerificationCode c = code(null, null);
        assertThat(c.isUsable(OffsetDateTime.now())).isFalse();
    }
}
