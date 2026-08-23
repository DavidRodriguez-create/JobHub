package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.SaveApplyProfileCommand;
import com.davidcreate.jobhub.auth.application.port.in.SaveApplyProfileUseCase;
import com.davidcreate.jobhub.auth.application.port.out.ApplyProfileRepository;
import com.davidcreate.jobhub.auth.domain.entity.ApplyProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * PUT /auth/account/apply-profile: load-or-create the single per-user bank (BR-3),
 * then full-replace it (BR-2). {@link ApplyProfile#replace} validates length/count caps
 * (BR-6) before this method ever calls the repository, so an invalid submission never
 * persists a partial change (BR-5 atomicity).
 */
@ApplicationScoped
@RequiredArgsConstructor
public class SaveApplyProfileService implements SaveApplyProfileUseCase {

    private final ApplyProfileRepository applyProfileRepository;

    @Override
    @Transactional
    public ApplyProfile save(UUID userId, SaveApplyProfileCommand command) {
        ApplyProfile existing = applyProfileRepository.findByUserId(userId).orElseGet(() -> ApplyProfile.empty(userId));

        ApplyProfile replaced = existing.replace(
                command.workAuthorization(),
                command.requiresSponsorship(),
                command.noticePeriod(),
                command.salaryExpectation(),
                command.currentLocation(),
                command.willingToRelocate(),
                command.linkedinUrl(),
                command.githubUrl(),
                command.portfolioUrl(),
                command.languages(),
                command.roomToGrow());

        return applyProfileRepository.save(replaced);
    }
}
