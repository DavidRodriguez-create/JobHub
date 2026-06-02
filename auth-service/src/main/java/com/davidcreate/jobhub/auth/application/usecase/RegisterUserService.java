package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.RegisterUserCommand;
import com.davidcreate.jobhub.auth.application.port.in.RegisterUserUseCase;
import com.davidcreate.jobhub.auth.application.port.in.SendEmailVerificationUseCase;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.EmailAlreadyRegisteredException;
import com.davidcreate.jobhub.auth.domain.exception.ValidationException;
import com.davidcreate.jobhub.auth.domain.valueobject.Email;
import com.davidcreate.jobhub.auth.domain.valueobject.Password;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class RegisterUserService implements RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final SendEmailVerificationUseCase sendEmailVerification;

    @Override
    @Transactional
    public User register(RegisterUserCommand command) {
        if (command.firstName() == null || command.firstName().isBlank()) {
            throw new ValidationException("firstName must not be blank");
        }
        if (command.lastName() == null || command.lastName().isBlank()) {
            throw new ValidationException("lastName must not be blank");
        }
        Email email = Email.of(command.email());
        Password password = Password.of(command.password());

        userRepository.findByEmail(email.value()).ifPresent(u -> {
            throw new EmailAlreadyRegisteredException(email.value());
        });

        User toSave = User.builder()
                .firstName(command.firstName().trim())
                .lastName(command.lastName().trim())
                .email(email.value())
                .passwordHash(passwordHasher.hash(password.raw()))
                .emailVerified(false)
                .build();

        User saved = userRepository.save(toSave);
        sendEmailVerification.sendFor(saved);
        return saved;
    }
}
