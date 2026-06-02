package com.davidcreate.jobhub.auth.application.port.in;

import java.util.UUID;

public interface ChangePasswordUseCase {
    void changePassword(UUID userId, ChangePasswordCommand command);
}
