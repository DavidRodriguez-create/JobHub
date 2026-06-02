package com.davidcreate.jobhub.auth.application.port.out;

import com.davidcreate.jobhub.auth.domain.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findUserById(UUID id);

    Optional<User> findByEmail(String email);

    User save(User user);

    void removeById(UUID id);
}
