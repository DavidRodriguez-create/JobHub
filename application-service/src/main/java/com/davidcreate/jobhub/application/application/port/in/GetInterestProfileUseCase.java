package com.davidcreate.jobhub.application.application.port.in;

import com.davidcreate.jobhub.application.domain.entity.InterestProfile;

import java.util.UUID;

public interface GetInterestProfileUseCase {

    InterestProfile getInterestProfile(UUID userId);
}
