package com.davidcreate.jobhub.auth.application.port.in;

import java.util.UUID;

public interface SetupTwoFactorUseCase {

    TwoFactorSetupResult setup(UUID userId);
}
