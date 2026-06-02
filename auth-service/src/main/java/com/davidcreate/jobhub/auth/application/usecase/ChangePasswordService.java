package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.ChangePasswordCommand;
import com.davidcreate.jobhub.auth.application.port.in.ChangePasswordUseCase;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.InvalidCredentialsException;
import com.davidcreate.jobhub.auth.domain.exception.UserNotFoundException;
import com.davidcreate.jobhub.auth.domain.valueobject.Password;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class ChangePasswordService implements ChangePasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    @Override
    @Transactional
    public void changePassword(UUID userId, ChangePasswordCommand command) {
        if (command.currentPassword() == null || command.currentPassword().isEmpty()) {
            throw new InvalidCredentialsException();
        }
        User user = userRepository.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!passwordHasher.matches(command.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        Password newPassword = Password.of(command.newPassword());
        userRepository.save(user.toBuilder()
                .passwordHash(passwordHasher.hash(newPassword.raw()))
                .build());
    }
}
