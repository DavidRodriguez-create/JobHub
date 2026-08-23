package com.davidcreate.jobhub.auth.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.BackupCodeEntity;
import com.davidcreate.jobhub.auth.adapter.out.persistence.mapper.BackupCodeMapper;
import com.davidcreate.jobhub.auth.domain.entity.BackupCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BackupCodeMapper Unit Tests")
class BackupCodeMapperTest {

    BackupCodeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new BackupCodeMapper();
    }

    @Test
    @DisplayName("TC-MAP-03: domain -> entity -> domain round-trip preserves all fields")
    void roundTripPreservesFields() {
        BackupCode original = BackupCode.builder()
                .id(UUID.randomUUID())
                .totpSecretId(UUID.randomUUID())
                .codeHash("bcrypt-hash")
                .consumedAt(null)
                .createdAt(OffsetDateTime.now())
                .build();

        BackupCodeEntity entity = mapper.toEntity(original);
        BackupCode roundTripped = mapper.toDomain(entity);

        assertThat(roundTripped.getId()).isEqualTo(original.getId());
        assertThat(roundTripped.getTotpSecretId()).isEqualTo(original.getTotpSecretId());
        assertThat(roundTripped.getCodeHash()).isEqualTo(original.getCodeHash());
        assertThat(roundTripped.getConsumedAt()).isNull();
        assertThat(roundTripped.getCreatedAt()).isEqualTo(original.getCreatedAt());
    }

    @Test
    @DisplayName("toDomain copies consumedAt when set")
    void toDomainCopiesConsumedAt() {
        BackupCodeEntity e = new BackupCodeEntity();
        e.id = UUID.randomUUID();
        e.totpSecretId = UUID.randomUUID();
        e.codeHash = "hash";
        e.consumedAt = OffsetDateTime.now();
        e.createdAt = OffsetDateTime.now();

        BackupCode d = mapper.toDomain(e);

        assertThat(d.getConsumedAt()).isEqualTo(e.consumedAt);
    }
}
