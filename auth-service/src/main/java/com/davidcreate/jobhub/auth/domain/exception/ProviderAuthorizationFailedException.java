package com.davidcreate.jobhub.auth.domain.exception;

public class ProviderAuthorizationFailedException extends RuntimeException {

    public ProviderAuthorizationFailedException() {
        super("the identity provider rejected the authorization code");
    }

    public ProviderAuthorizationFailedException(Throwable cause) {
        super("the identity provider rejected the authorization code", cause);
    }
}
