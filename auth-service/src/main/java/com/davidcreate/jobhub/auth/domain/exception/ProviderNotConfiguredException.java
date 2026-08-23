package com.davidcreate.jobhub.auth.domain.exception;

public class ProviderNotConfiguredException extends RuntimeException {
    public ProviderNotConfiguredException(String provider) {
        super("unknown or unconfigured oauth provider: " + provider);
    }
}
