package com.davidcreate.jobhub.auth.application.port.in;

import com.davidcreate.jobhub.auth.domain.entity.ApplyProfile;

import java.util.UUID;

public interface SaveApplyProfileUseCase {
    ApplyProfile save(UUID userId, SaveApplyProfileCommand command);
}
