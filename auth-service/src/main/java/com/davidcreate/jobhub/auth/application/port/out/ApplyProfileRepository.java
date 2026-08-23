package com.davidcreate.jobhub.auth.application.port.out;

import com.davidcreate.jobhub.auth.domain.entity.ApplyProfile;

import java.util.Optional;
import java.util.UUID;

public interface ApplyProfileRepository {

    Optional<ApplyProfile> findByUserId(UUID userId);

    ApplyProfile save(ApplyProfile profile);
}
