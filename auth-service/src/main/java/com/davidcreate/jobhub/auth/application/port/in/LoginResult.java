package com.davidcreate.jobhub.auth.application.port.in;

import com.davidcreate.jobhub.auth.domain.entity.User;

/**
 * When {@code twoFactorToken} is non-null, the account has 2FA enabled and
 * login step 1 has not yet been completed: {@code token}, {@code user} are
 * null and the client must call {@code POST /auth/login/2fa} with the
 * challenge token and a TOTP/backup code (ADR 0012). Otherwise this is a
 * complete login (no 2FA, or 2FA step 2 succeeded).
 */
public record LoginResult(String token, long expiresInSeconds, User user, String twoFactorToken) {

    public LoginResult(String token, long expiresInSeconds, User user) {
        this(token, expiresInSeconds, user, null);
    }

    public boolean isTwoFactorRequired() {
        return twoFactorToken != null;
    }
}
