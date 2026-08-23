package com.davidcreate.jobhub.auth.application.port.in;

import java.util.UUID;

public interface DisableTwoFactorUseCase {

    void disable(UUID userId, String totpCode);
}
