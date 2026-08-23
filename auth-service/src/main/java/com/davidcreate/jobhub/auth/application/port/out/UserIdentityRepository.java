package com.davidcreate.jobhub.auth.application.port.out;

import com.davidcreate.jobhub.auth.domain.entity.UserIdentity;

import java.util.Optional;

public interface UserIdentityRepository {

    Optional<UserIdentity> findByProviderAndSubject(String provider, String providerUserId);

    UserIdentity save(UserIdentity identity);
}
