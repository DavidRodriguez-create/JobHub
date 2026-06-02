package com.davidcreate.jobhub.auth.application.port.in;

import com.davidcreate.jobhub.auth.domain.entity.User;

public interface RegisterUserUseCase {
    User register(RegisterUserCommand command);
}
