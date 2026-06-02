package com.davidcreate.jobhub.auth.application.port.in;

import com.davidcreate.jobhub.auth.domain.entity.User;

import java.util.UUID;

public interface UpdateCurrentUserUseCase {
    User update(UUID userId, UpdateCurrentUserCommand command);
}
