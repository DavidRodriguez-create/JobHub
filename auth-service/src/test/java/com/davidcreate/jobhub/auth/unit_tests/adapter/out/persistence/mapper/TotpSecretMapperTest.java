package com.davidcreate.jobhub.auth.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.TotpSecretEntity;
import com.davidcreate.jobhub.auth.adapter.out.persistence.mapper.TotpSecretMapper;
import com.davidcreate.jobhub.auth.domain.entity.TotpSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TotpSecretMapper Unit Tests")
class TotpSecretMapperTest {

    TotpSecretMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TotpSecretMapper();
    }

    @Test
    @DisplayName("TC-MAP-01: domain -> entity -> domain round-trip preserves all fields")
    void roundTripPreservesFields() {
        TotpSecret original = TotpSecret.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .encryptedSecret("iv:ciphertext")
                .verified(true)
                .verifiedAt(OffsetDateTime.now().minusMinutes(5))
                .createdAt(OffsetDateTime.now().minusDays(1))
                .build();

        TotpSecretEntity entity = mapper.toEntity(original);
        TotpSecret roundTripped = mapper.toDomain(entity);

        assertThat(roundTripped.getId()).isEqualTo(original.getId());
        assertThat(roundTripped.getUserId()).isEqualTo(original.getUserId());
        assertThat(roundTripped.getEncryptedSecret()).isEqualTo(original.getEncryptedSecret());
        assertThat(roundTripped.isVerified()).isEqualTo(original.isVerified());
        assertThat(roundTripped.getVerifiedAt()).isEqualTo(original.getVerifiedAt());
        assertThat(roundTripped.getCreatedAt()).isEqualTo(original.getCreatedAt());
    }

    @Test
    @DisplayName("toDomain copies all fields from entity")
    void toDomainCopiesFields() {
        TotpSecretEntity e = new TotpSecretEntity();
        e.id = UUID.randomUUID();
        e.userId = UUID.randomUUID();
        e.encryptedSecret = "enc";
        e.verified = false;
        e.verifiedAt = null;
        e.createdAt = OffsetDateTime.now();

        TotpSecret d = mapper.toDomain(e);

        assertThat(d.getId()).isEqualTo(e.id);
        assertThat(d.getUserId()).isEqualTo(e.userId);
        assertThat(d.getEncryptedSecret()).isEqualTo(e.encryptedSecret);
        assertThat(d.isVerified()).isFalse();
        assertThat(d.getVerifiedAt()).isNull();
    }
}
