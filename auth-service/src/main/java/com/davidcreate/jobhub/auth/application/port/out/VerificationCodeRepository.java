package com.davidcreate.jobhub.auth.application.port.out;

import com.davidcreate.jobhub.auth.domain.entity.VerificationCode;

import java.util.Optional;
import java.util.UUID;

public interface VerificationCodeRepository {

    VerificationCode save(VerificationCode code);

    Optional<VerificationCode> findOneById(UUID id);
}
