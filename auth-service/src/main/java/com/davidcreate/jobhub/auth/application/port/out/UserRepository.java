package com.davidcreate.jobhub.auth.application.port.out;

import com.davidcreate.jobhub.auth.domain.entity.User;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findUserById(UUID id);

    Optional<User> findByEmail(String email);

    /**
     * Returns the users matching the given IDs. IDs that do not correspond to an
     * existing user are simply absent from the result (no exception, no null entries).
     */
    List<User> findByIds(Collection<UUID> ids);

    User save(User user);

    void removeById(UUID id);

    /**
     * Returns the IDs of users created on or before {@code since} who still do
     * not have 2FA enabled. Used by the notification-service security-recommendation
     * scheduler (ADR 0012).
     */
    List<UUID> findUserIdsWithoutTwoFactorSince(OffsetDateTime since);
}
