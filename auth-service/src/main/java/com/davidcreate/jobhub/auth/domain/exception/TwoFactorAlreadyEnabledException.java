package com.davidcreate.jobhub.auth.domain.exception;

public class TwoFactorAlreadyEnabledException extends RuntimeException {

    public TwoFactorAlreadyEnabledException() {
        super("two-factor authentication is already enabled on this account");
    }
}
