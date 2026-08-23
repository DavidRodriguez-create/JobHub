package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.GetApplyProfileUseCase;
import com.davidcreate.jobhub.auth.application.port.out.ApplyProfileRepository;
import com.davidcreate.jobhub.auth.domain.entity.ApplyProfile;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * GET /auth/account/apply-profile always returns 200 (AC1): a user who has never
 * saved a bank gets the all-null shape from {@link ApplyProfile#empty(UUID)}, never a
 * 404/exception.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class GetApplyProfileService implements GetApplyProfileUseCase {

    private final ApplyProfileRepository applyProfileRepository;

    @Override
    public ApplyProfile get(UUID userId) {
        return applyProfileRepository.findByUserId(userId).orElseGet(() -> ApplyProfile.empty(userId));
    }
}
