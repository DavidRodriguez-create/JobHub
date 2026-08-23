package com.davidcreate.jobhub.auth.domain.exception;

public class TwoFactorNotEnabledException extends RuntimeException {

    public TwoFactorNotEnabledException() {
        super("two-factor authentication is not enabled on this account");
    }
}
