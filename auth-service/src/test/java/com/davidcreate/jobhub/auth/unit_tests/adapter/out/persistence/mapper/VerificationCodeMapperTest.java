package com.davidcreate.jobhub.auth.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.VerificationCodeEntity;
import com.davidcreate.jobhub.auth.adapter.out.persistence.mapper.VerificationCodeMapper;
import com.davidcreate.jobhub.auth.domain.entity.VerificationCode;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EV-U-14: VerificationCodeMapper maps VERIFY_EMAIL action correctly in both directions.
 */
@DisplayName("VerificationCodeMapper — EV-U-14")
class VerificationCodeMapperTest {

    private final VerificationCodeMapper mapper = new VerificationCodeMapper();

    @Test
    @DisplayName("EV-U-14a: toEntity maps VERIFY_EMAIL action to 'verify-email' string")
    void toEntityMapsVerifyEmailAction() {
        VerificationCode domain = VerificationCode.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .action(VerificationAction.VERIFY_EMAIL)
                .codeHash("hash123")
                .expiresAt(OffsetDateTime.now().plusMinutes(15))
                .build();

        VerificationCodeEntity entity = mapper.toEntity(domain);

        assertThat(entity.action).isEqualTo("verify-email");
    }

    @Test
    @DisplayName("EV-U-14b: toDomain maps 'verify-email' string to VERIFY_EMAIL enum")
    void toDomainMapsVerifyEmailString() {
        VerificationCodeEntity entity = new VerificationCodeEntity();
        entity.id = UUID.randomUUID();
        entity.userId = UUID.randomUUID();
        entity.action = "verify-email";
        entity.codeHash = "hash123";
        entity.expiresAt = OffsetDateTime.now().plusMinutes(15);
        entity.createdAt = OffsetDateTime.now();

        VerificationCode domain = mapper.toDomain(entity);

        assertThat(domain.getAction()).isEqualTo(VerificationAction.VERIFY_EMAIL);
    }

    @Test
    @DisplayName("EV-U-14c: existing DELETE_ACCOUNT action still maps correctly")
    void existingActionsUnaffected() {
        VerificationCodeEntity entity = new VerificationCodeEntity();
        entity.id = UUID.randomUUID();
        entity.userId = UUID.randomUUID();
        entity.action = "delete-account";
        entity.codeHash = "h";
        entity.expiresAt = OffsetDateTime.now().plusMinutes(15);
        entity.createdAt = OffsetDateTime.now();

        VerificationCode domain = mapper.toDomain(entity);
        assertThat(domain.getAction()).isEqualTo(VerificationAction.DELETE_ACCOUNT);

        VerificationCodeEntity back = mapper.toEntity(domain);
        assertThat(back.action).isEqualTo("delete-account");
    }
}
