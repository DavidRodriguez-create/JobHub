package com.davidcreate.jobhub.auth.adapter.in.rest.dto;

import com.davidcreate.jobhub.auth.application.port.in.LoginResult;
import com.davidcreate.jobhub.auth.contract.model.AccountResponse;
import com.davidcreate.jobhub.auth.contract.model.LoginResponse;
import com.davidcreate.jobhub.auth.domain.entity.User;

public final class AccountResponseMapper {

    private AccountResponseMapper() {
    }

    public static AccountResponse toAccount(User user) {
        return new AccountResponse()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .emailVerified(user.isEmailVerified());
    }

    public static LoginResponse toLogin(LoginResult result) {
        return new LoginResponse()
                .token(result.token())
                .expiresIn(result.expiresInSeconds())
                .account(toAccount(result.user()));
    }
}
