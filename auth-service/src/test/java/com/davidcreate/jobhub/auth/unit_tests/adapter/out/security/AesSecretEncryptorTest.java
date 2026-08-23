package com.davidcreate.jobhub.auth.unit_tests.adapter.out.security;

import com.davidcreate.jobhub.auth.adapter.out.security.AesSecretEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AesSecretEncryptor Unit Tests")
class AesSecretEncryptorTest {

    private static final String KEY_HEX = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    AesSecretEncryptor encryptor;

    @BeforeEach
    void setUp() throws Exception {
        encryptor = new AesSecretEncryptor();
        Field f = AesSecretEncryptor.class.getDeclaredField("encryptionKeyHex");
        f.setAccessible(true);
        f.set(encryptor, KEY_HEX);
    }

    @Test
    @DisplayName("encrypt then decrypt round-trip returns original plaintext")
    void roundTrip() {
        String plaintext = "JBSWY3DPEHPK3PXP";
        String ciphertext = encryptor.encrypt(plaintext);

        assertThat(ciphertext).isNotEqualTo(plaintext);
        assertThat(encryptor.decrypt(ciphertext)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("each encryption produces a different ciphertext (random IV)")
    void ciphertextDiffersPerCall() {
        String plaintext = "JBSWY3DPEHPK3PXP";
        assertThat(encryptor.encrypt(plaintext)).isNotEqualTo(encryptor.encrypt(plaintext));
    }

    @Test
    @DisplayName("decrypting a tampered ciphertext fails")
    void decryptRejectsTamperedCiphertext() {
        String ciphertext = encryptor.encrypt("JBSWY3DPEHPK3PXP");
        String tampered = ciphertext.substring(0, ciphertext.length() - 4) + "abcd";

        assertThatThrownBy(() -> encryptor.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }
}
