package com.davidcreate.jobhub.auth.application.port.in;

public interface LoginUseCase {
    LoginResult login(LoginCommand command);
}
