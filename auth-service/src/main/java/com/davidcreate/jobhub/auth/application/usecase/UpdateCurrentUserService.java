package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.UpdateCurrentUserCommand;
import com.davidcreate.jobhub.auth.application.port.in.UpdateCurrentUserUseCase;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class UpdateCurrentUserService implements UpdateCurrentUserUseCase {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public User update(UUID userId, UpdateCurrentUserCommand command) {
        User existing = userRepository.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        User.UserBuilder updated = existing.toBuilder();

        if (command.firstName() != null && !command.firstName().isBlank()) {
            updated.firstName(command.firstName().trim());
        }
        if (command.lastName() != null && !command.lastName().isBlank()) {
            updated.lastName(command.lastName().trim());
        }

        return userRepository.save(updated.build());
    }
}
