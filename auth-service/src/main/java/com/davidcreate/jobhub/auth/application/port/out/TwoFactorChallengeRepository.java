package com.davidcreate.jobhub.auth.application.port.out;

import com.davidcreate.jobhub.auth.domain.entity.TwoFactorChallenge;

import java.util.Optional;
import java.util.UUID;

public interface TwoFactorChallengeRepository {

    TwoFactorChallenge save(TwoFactorChallenge challenge);

    Optional<TwoFactorChallenge> findOneById(UUID id);
}
