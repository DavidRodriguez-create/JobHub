package com.davidcreate.jobhub.application.application.usecase;

import com.davidcreate.jobhub.application.application.port.in.GetInterestProfileUseCase;
import com.davidcreate.jobhub.application.application.port.out.InterestProfileRepository;
import com.davidcreate.jobhub.application.domain.entity.InterestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class GetInterestProfileHandler implements GetInterestProfileUseCase {

    private final InterestProfileRepository repository;

    @Override
    public InterestProfile getInterestProfile(UUID userId) {
        return repository.findInterestProfile(userId);
    }
}
