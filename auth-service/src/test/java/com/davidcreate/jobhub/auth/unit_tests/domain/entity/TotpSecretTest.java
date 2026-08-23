package com.davidcreate.jobhub.auth.unit_tests.domain.entity;

import com.davidcreate.jobhub.auth.domain.entity.TotpSecret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TotpSecret Unit Tests — TC-DOM-01")
class TotpSecretTest {

    @Test
    @DisplayName("TC-DOM-01: isActive() returns false when unverified")
    void unverifiedIsNotActive() {
        TotpSecret secret = TotpSecret.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .encryptedSecret("enc")
                .verified(false)
                .build();

        assertThat(secret.isActive()).isFalse();
    }

    @Test
    @DisplayName("TC-DOM-01: isActive() returns true when verified")
    void verifiedIsActive() {
        TotpSecret secret = TotpSecret.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .encryptedSecret("enc")
                .verified(true)
                .build();

        assertThat(secret.isActive()).isTrue();
    }
}
