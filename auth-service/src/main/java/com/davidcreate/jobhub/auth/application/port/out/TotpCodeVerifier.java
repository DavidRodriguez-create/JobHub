package com.davidcreate.jobhub.auth.application.port.out;

public interface TotpCodeVerifier {

    /** Generates a new random base32-encoded TOTP secret. */
    String generateSecret();

    /** Builds the otpauth:// URI for QR code display (issuer + account name + secret). */
    String buildOtpAuthUri(String base32Secret, String accountEmail);

    /** Validates a 6-digit code against the given base32 secret. */
    boolean verify(String base32Secret, String code);
}
