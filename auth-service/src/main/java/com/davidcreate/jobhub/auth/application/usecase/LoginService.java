package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.LoginCommand;
import com.davidcreate.jobhub.auth.application.port.in.LoginResult;
import com.davidcreate.jobhub.auth.application.port.in.LoginUseCase;
import com.davidcreate.jobhub.auth.application.port.out.PasswordHasher;
import com.davidcreate.jobhub.auth.application.port.out.TokenGenerator;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.exception.InvalidCredentialsException;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenGenerator tokenGenerator;

    @Override
    public LoginResult login(LoginCommand command) {
        if (command.email() == null || command.password() == null) {
            throw new InvalidCredentialsException();
        }
        User user = userRepository.findByEmail(command.email().trim().toLowerCase())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordHasher.matches(command.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = tokenGenerator.generate(user);
        return new LoginResult(token, tokenGenerator.lifespanSeconds(), user);
    }
}
