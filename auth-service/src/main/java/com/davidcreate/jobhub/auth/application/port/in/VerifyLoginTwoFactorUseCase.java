package com.davidcreate.jobhub.auth.application.port.in;

public interface VerifyLoginTwoFactorUseCase {

    LoginResult verify(VerifyLoginTwoFactorCommand command);
}
