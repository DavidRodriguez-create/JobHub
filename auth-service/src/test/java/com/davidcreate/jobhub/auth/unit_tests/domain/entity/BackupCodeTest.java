package com.davidcreate.jobhub.auth.unit_tests.domain.entity;

import com.davidcreate.jobhub.auth.domain.entity.BackupCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BackupCode Unit Tests — TC-DOM-05..06")
class BackupCodeTest {

    @Test
    @DisplayName("TC-DOM-05: isUsable() returns false when consumed")
    void consumedIsNotUsable() {
        BackupCode code = BackupCode.builder()
                .id(UUID.randomUUID())
                .totpSecretId(UUID.randomUUID())
                .codeHash("hash")
                .consumedAt(OffsetDateTime.now())
                .build();

        assertThat(code.isUsable()).isFalse();
    }

    @Test
    @DisplayName("TC-DOM-06: isUsable() returns true when unconsumed")
    void freshIsUsable() {
        BackupCode code = BackupCode.builder()
                .id(UUID.randomUUID())
                .totpSecretId(UUID.randomUUID())
                .codeHash("hash")
                .build();

        assertThat(code.isUsable()).isTrue();
    }
}
