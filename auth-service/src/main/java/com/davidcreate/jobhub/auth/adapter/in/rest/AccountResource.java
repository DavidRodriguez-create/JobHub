package com.davidcreate.jobhub.auth.adapter.in.rest;

import com.davidcreate.jobhub.auth.adapter.in.rest.dto.AccountResponseMapper;
import com.davidcreate.jobhub.auth.application.port.in.ChangePasswordCommand;
import com.davidcreate.jobhub.auth.application.port.in.ChangePasswordUseCase;
import com.davidcreate.jobhub.auth.application.port.in.ConsumeVerificationUseCase;
import com.davidcreate.jobhub.auth.application.port.in.DeleteAccountUseCase;
import com.davidcreate.jobhub.auth.application.port.in.GetCurrentUserUseCase;
import com.davidcreate.jobhub.auth.application.port.in.RequestVerificationUseCase;
import com.davidcreate.jobhub.auth.application.port.in.ResendVerificationUseCase;
import com.davidcreate.jobhub.auth.application.port.in.UpdateCurrentUserCommand;
import com.davidcreate.jobhub.auth.application.port.in.UpdateCurrentUserUseCase;
import com.davidcreate.jobhub.auth.application.port.in.VerifyEmailUseCase;
import com.davidcreate.jobhub.auth.contract.api.AccountApi;
import com.davidcreate.jobhub.auth.contract.model.ChangePasswordRequest;
import com.davidcreate.jobhub.auth.contract.model.ConsumeVerificationRequest;
import com.davidcreate.jobhub.auth.contract.model.ResendVerificationRequest;
import com.davidcreate.jobhub.auth.contract.model.UpdateAccountRequest;
import com.davidcreate.jobhub.auth.contract.model.VerificationRequest;
import com.davidcreate.jobhub.auth.contract.model.VerificationResponse;
import com.davidcreate.jobhub.auth.contract.model.VerifiedActionRequest;
import com.davidcreate.jobhub.auth.contract.model.VerifyEmailRequest;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@ApplicationScoped
@Path("/account")
@RequiredArgsConstructor
public class AccountResource implements AccountApi {

    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final UpdateCurrentUserUseCase updateCurrentUserUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;
    private final RequestVerificationUseCase requestVerificationUseCase;
    private final ConsumeVerificationUseCase consumeVerificationUseCase;
    private final DeleteAccountUseCase deleteAccountUseCase;
    private final VerifyEmailUseCase verifyEmailUseCase;
    private final ResendVerificationUseCase resendVerificationUseCase;
    private final JsonWebToken jwt;

    @Override
    @RolesAllowed("user")
    public Response getAccount() {
        return Response.ok(AccountResponseMapper.toAccount(getCurrentUserUseCase.get(currentUserId()))).build();
    }

    @Override
    @RolesAllowed("user")
    public Response updateAccount(UpdateAccountRequest req) {
        User updated = updateCurrentUserUseCase.update(currentUserId(),
                new UpdateCurrentUserCommand(req.getFirstName(), req.getLastName()));
        return Response.ok(AccountResponseMapper.toAccount(updated)).build();
    }

    @Override
    @RolesAllowed("user")
    public Response changePassword(ChangePasswordRequest req) {
        changePasswordUseCase.changePassword(currentUserId(),
                new ChangePasswordCommand(req.getCurrentPassword(), req.getNewPassword()));
        return Response.noContent().build();
    }

    @Override
    @RolesAllowed("user")
    public Response requestVerification(VerificationRequest req) {
        VerificationAction action = VerificationAction.fromValue(req.getAction().value());
        RequestVerificationUseCase.VerificationResult result =
                requestVerificationUseCase.request(currentUserId(), action);
        return Response.ok(new VerificationResponse()
                        .verificationId(result.verificationId())
                        .expiresAt(result.expiresAt()))
                .build();
    }

    @Override
    @RolesAllowed("user")
    public Response consumeVerification(ConsumeVerificationRequest req) {
        VerificationAction action = VerificationAction.fromValue(req.getAction().value());
        consumeVerificationUseCase.consume(currentUserId(), req.getVerificationId(), req.getCode(), action);
        return Response.noContent().build();
    }

    @Override
    @RolesAllowed("user")
    public Response deleteAccount(VerifiedActionRequest req) {
        deleteAccountUseCase.delete(currentUserId(), req.getVerificationId(), req.getCode());
        return Response.noContent().build();
    }

    @Override
    @PermitAll
    public Response verifyEmail(VerifyEmailRequest req) {
        verifyEmailUseCase.verify(req.getToken());
        return Response.noContent().build();
    }

    @Override
    @PermitAll
    public Response resendVerification(ResendVerificationRequest req) {
        resendVerificationUseCase.resend(req.getEmail());
        return Response.noContent().build();
    }

    private UUID currentUserId() {
        return UUID.fromString(jwt.getSubject());
    }
}
