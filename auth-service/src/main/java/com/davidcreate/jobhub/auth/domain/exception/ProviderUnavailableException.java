package com.davidcreate.jobhub.auth.domain.exception;

public class ProviderUnavailableException extends RuntimeException {

    public ProviderUnavailableException() {
        super("the identity provider is unavailable right now");
    }

    public ProviderUnavailableException(Throwable cause) {
        super("the identity provider is unavailable right now", cause);
    }
}
