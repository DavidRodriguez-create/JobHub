package com.davidcreate.jobhub.auth.application.port.in;

import java.util.List;
import java.util.UUID;

public interface RegenerateBackupCodesUseCase {

    List<String> regenerate(UUID userId, String totpCode);
}
