package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.GetCurrentUserUseCase;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class GetCurrentUserService implements GetCurrentUserUseCase {

    private final UserRepository userRepository;

    @Override
    public User get(UUID userId) {
        return userRepository.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
