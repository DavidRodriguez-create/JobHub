package com.davidcreate.jobhub.auth.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.GetUserEmailsUseCase;
import com.davidcreate.jobhub.auth.application.port.in.UserEmailResult;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class GetUserEmailsService implements GetUserEmailsUseCase {

    private final UserRepository userRepository;

    @Override
    public List<UserEmailResult> getEmails(List<UUID> userIds) {
        return userRepository.findByIds(userIds).stream()
                .filter(user -> user.isEmailVerified())
                .map(user -> new UserEmailResult(user.getId(), user.getEmail()))
                .toList();
    }
}
