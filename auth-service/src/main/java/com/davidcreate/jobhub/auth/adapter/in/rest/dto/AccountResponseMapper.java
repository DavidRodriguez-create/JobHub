package com.davidcreate.jobhub.auth.adapter.in.rest.dto;

import com.davidcreate.jobhub.auth.application.port.in.LoginResult;
import com.davidcreate.jobhub.auth.contract.model.AccountResponse;
import com.davidcreate.jobhub.auth.contract.model.LoginResponse;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.valueobject.AdminAllowlist;

import java.util.List;

public final class AccountResponseMapper {

    private AccountResponseMapper() {
    }

    public static AccountResponse toAccount(User user) {
        return toAccount(user, false, List.of(AdminAllowlist.USER_GROUP));
    }

    public static AccountResponse toAccount(User user, boolean isAdmin, List<String> groups) {
        return new AccountResponse()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .emailVerified(user.isEmailVerified())
                .twoFactorEnabled(user.isTwoFactorEnabled())
                .isAdmin(isAdmin)
                .groups(groups);
    }

    public static LoginResponse toLogin(LoginResult result, boolean isAdmin, List<String> groups) {
        if (result.isTwoFactorRequired()) {
            return new LoginResponse()
                    .twoFactorRequired(true)
                    .twoFactorToken(result.twoFactorToken());
        }
        return new LoginResponse()
                .token(result.token())
                .expiresIn(result.expiresInSeconds())
                .account(toAccount(result.user(), isAdmin, groups))
                .twoFactorRequired(false);
    }
}
