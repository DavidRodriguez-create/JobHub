package com.davidcreate.jobhub.auth.unit_tests.adapter.out.security;

import com.davidcreate.jobhub.auth.adapter.out.security.TotpCodeVerifierAdapter;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TotpCodeVerifierAdapter Unit Tests")
class TotpCodeVerifierAdapterTest {

    TotpCodeVerifierAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        adapter = new TotpCodeVerifierAdapter();
        Field f = TotpCodeVerifierAdapter.class.getDeclaredField("issuer");
        f.setAccessible(true);
        f.set(adapter, "JobHub");
    }

    @Test
    @DisplayName("generateSecret returns a non-blank base32 secret each time")
    void generateSecretIsRandom() {
        String first = adapter.generateSecret();
        String second = adapter.generateSecret();

        assertThat(first).isNotBlank();
        assertThat(second).isNotBlank();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("buildOtpAuthUri produces an otpauth:// URI containing the issuer and account email")
    void buildOtpAuthUriContainsIssuerAndLabel() {
        String secret = adapter.generateSecret();
        String uri = adapter.buildOtpAuthUri(secret, "alice@example.com");

        assertThat(uri).startsWith("otpauth://totp/");
        assertThat(uri).contains("issuer=JobHub");
        assertThat(uri).contains("alice%40example.com");
    }

    @Test
    @DisplayName("verify accepts a code generated for the current time window")
    void verifyAcceptsValidCode() throws Exception {
        String secret = adapter.generateSecret();
        long currentBucket = Instant.now().getEpochSecond() / 30;
        String code = new DefaultCodeGenerator().generate(secret, currentBucket);

        assertThat(adapter.verify(secret, code)).isTrue();
    }

    @Test
    @DisplayName("verify rejects an incorrect code")
    void verifyRejectsWrongCode() {
        String secret = adapter.generateSecret();
        assertThat(adapter.verify(secret, "000000")).isFalse();
    }

    @Test
    @DisplayName("verify rejects null or blank code")
    void verifyRejectsNullOrBlank() {
        String secret = adapter.generateSecret();
        assertThat(adapter.verify(secret, null)).isFalse();
        assertThat(adapter.verify(secret, "")).isFalse();
        assertThat(adapter.verify(secret, "   ")).isFalse();
    }
}
