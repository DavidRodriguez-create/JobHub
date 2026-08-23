package com.davidcreate.jobhub.auth.application.port.in;

import java.util.List;
import java.util.UUID;

public interface VerifyTwoFactorSetupUseCase {

    List<String> verifySetup(UUID userId, String totpCode);
}
