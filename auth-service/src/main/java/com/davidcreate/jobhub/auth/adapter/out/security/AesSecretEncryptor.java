package com.davidcreate.jobhub.auth.adapter.out.security;

import com.davidcreate.jobhub.auth.application.port.out.SecretEncryptor;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AES-256-GCM encryption for TOTP secrets at rest (ADR 0012). The key is a
 * 32-byte (256-bit) value supplied as a hex string via
 * {@code auth.totp.encryption-key}. Each ciphertext is prefixed with a fresh
 * random 12-byte IV, base64-encoded as {@code iv:ciphertext}.
 */
@ApplicationScoped
public class AesSecretEncryptor implements SecretEncryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecureRandom random = new SecureRandom();

    @ConfigProperty(name = "auth.totp.encryption-key")
    String encryptionKeyHex;

    @Override
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            String ivPart = Base64.getEncoder().encodeToString(iv);
            String ciphertextPart = Base64.getEncoder().encodeToString(ciphertext);
            return ivPart + ":" + ciphertextPart;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt TOTP secret", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        try {
            String[] parts = ciphertext.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Malformed encrypted secret");
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt TOTP secret", e);
        }
    }

    private SecretKeySpec secretKey() {
        byte[] keyBytes = HexFormat.of().parseHex(encryptionKeyHex);
        return new SecretKeySpec(keyBytes, "AES");
    }
}
