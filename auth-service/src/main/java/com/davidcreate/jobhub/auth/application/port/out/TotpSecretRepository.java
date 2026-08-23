package com.davidcreate.jobhub.auth.application.port.out;

import com.davidcreate.jobhub.auth.domain.entity.TotpSecret;

import java.util.Optional;
import java.util.UUID;

public interface TotpSecretRepository {

    TotpSecret save(TotpSecret secret);

    Optional<TotpSecret> findByUserId(UUID userId);

    void removeByUserId(UUID userId);
}
