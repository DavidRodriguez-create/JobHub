package com.davidcreate.jobhub.auth.application.port.in;

import com.davidcreate.jobhub.auth.domain.entity.ApplyProfile;

import java.util.UUID;

public interface GetApplyProfileUseCase {
    ApplyProfile get(UUID userId);
}
