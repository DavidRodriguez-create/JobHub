package com.davidcreate.jobhub.auth.adapter.in.rest;

import com.davidcreate.jobhub.auth.adapter.in.rest.dto.AccountResponseMapper;
import com.davidcreate.jobhub.auth.adapter.in.rest.dto.ApplyProfileMapper;
import com.davidcreate.jobhub.auth.application.port.in.ChangePasswordCommand;
import com.davidcreate.jobhub.auth.application.port.in.ChangePasswordUseCase;
import com.davidcreate.jobhub.auth.application.port.in.ConsumeVerificationUseCase;
import com.davidcreate.jobhub.auth.application.port.in.DeleteAccountUseCase;
import com.davidcreate.jobhub.auth.application.port.in.DisableTwoFactorUseCase;
import com.davidcreate.jobhub.auth.application.port.in.GetApplyProfileUseCase;
import com.davidcreate.jobhub.auth.application.port.in.GetCurrentUserUseCase;
import com.davidcreate.jobhub.auth.application.port.in.RegenerateBackupCodesUseCase;
import com.davidcreate.jobhub.auth.application.port.in.RequestVerificationUseCase;
import com.davidcreate.jobhub.auth.application.port.in.ResendVerificationUseCase;
import com.davidcreate.jobhub.auth.application.port.in.SaveApplyProfileUseCase;
import com.davidcreate.jobhub.auth.application.port.in.SetupTwoFactorUseCase;
import com.davidcreate.jobhub.auth.application.port.in.TwoFactorSetupResult;
import com.davidcreate.jobhub.auth.application.port.in.UpdateCurrentUserCommand;
import com.davidcreate.jobhub.auth.application.port.in.UpdateCurrentUserUseCase;
import com.davidcreate.jobhub.auth.application.port.in.VerifyEmailUseCase;
import com.davidcreate.jobhub.auth.application.port.in.VerifyTwoFactorSetupUseCase;
import com.davidcreate.jobhub.auth.contract.api.AccountApi;
import com.davidcreate.jobhub.auth.contract.model.AccountResponse;
import com.davidcreate.jobhub.auth.contract.model.ApplyProfileRequest;
import com.davidcreate.jobhub.auth.contract.model.BackupCodesResponse;
import com.davidcreate.jobhub.auth.contract.model.ChangePasswordRequest;
import com.davidcreate.jobhub.auth.contract.model.ConsumeVerificationRequest;
import com.davidcreate.jobhub.auth.contract.model.DisableTwoFactorRequest;
import com.davidcreate.jobhub.auth.contract.model.RegenerateBackupCodesRequest;
import com.davidcreate.jobhub.auth.contract.model.ResendVerificationRequest;
import com.davidcreate.jobhub.auth.contract.model.TwoFactorSetupResponse;
import com.davidcreate.jobhub.auth.contract.model.TwoFactorVerifySetupResponse;
import com.davidcreate.jobhub.auth.contract.model.UpdateAccountRequest;
import com.davidcreate.jobhub.auth.contract.model.VerificationRequest;
import com.davidcreate.jobhub.auth.contract.model.VerificationResponse;
import com.davidcreate.jobhub.auth.contract.model.VerifiedActionRequest;
import com.davidcreate.jobhub.auth.contract.model.VerifyEmailRequest;
import com.davidcreate.jobhub.auth.contract.model.VerifyTwoFactorSetupRequest;
import com.davidcreate.jobhub.auth.domain.entity.User;
import com.davidcreate.jobhub.auth.domain.valueobject.AdminAllowlist;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Optional;
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
    private final SetupTwoFactorUseCase setupTwoFactorUseCase;
    private final VerifyTwoFactorSetupUseCase verifyTwoFactorSetupUseCase;
    private final DisableTwoFactorUseCase disableTwoFactorUseCase;
    private final RegenerateBackupCodesUseCase regenerateBackupCodesUseCase;
    private final GetApplyProfileUseCase getApplyProfileUseCase;
    private final SaveApplyProfileUseCase saveApplyProfileUseCase;
    private final JsonWebToken jwt;

    @ConfigProperty(name = "auth.admin.emails")
    Optional<String> adminEmailsConfig;

    @Override
    @RolesAllowed("user")
    public Response getAccount() {
        User user = getCurrentUserUseCase.get(currentUserId());
        return Response.ok(toAccountResponse(user)).build();
    }

    @Override
    @RolesAllowed("user")
    public Response updateAccount(UpdateAccountRequest req) {
        User updated = updateCurrentUserUseCase.update(currentUserId(),
                new UpdateCurrentUserCommand(req.getFirstName(), req.getLastName()));
        return Response.ok(toAccountResponse(updated)).build();
    }

    @Override
    @RolesAllowed("user")
    public Response changePassword(ChangePasswordRequest req) {
        changePasswordUseCase.changePassword(currentUserId(),
                new ChangePasswordCommand(req.getCurrentPassword(), req.getNewPassword(), req.getTotpCode()));
        return Response.noContent().build();
    }

    @Override
    @RolesAllowed("user")
    public Response setupTwoFactor() {
        TwoFactorSetupResult result = setupTwoFactorUseCase.setup(currentUserId());
        return Response.ok(new TwoFactorSetupResponse()
                        .otpauthUri(result.otpauthUri())
                        .setupKey(result.setupKey()))
                .build();
    }

    @Override
    @RolesAllowed("user")
    public Response verifyTwoFactorSetup(VerifyTwoFactorSetupRequest req) {
        var backupCodes = verifyTwoFactorSetupUseCase.verifySetup(currentUserId(), req.getTotpCode());
        return Response.ok(new TwoFactorVerifySetupResponse().backupCodes(backupCodes)).build();
    }

    @Override
    @RolesAllowed("user")
    public Response disableTwoFactor(DisableTwoFactorRequest req) {
        disableTwoFactorUseCase.disable(currentUserId(), req.getTotpCode());
        return Response.noContent().build();
    }

    @Override
    @RolesAllowed("user")
    public Response regenerateBackupCodes(RegenerateBackupCodesRequest req) {
        var backupCodes = regenerateBackupCodesUseCase.regenerate(currentUserId(), req.getTotpCode());
        return Response.ok(new BackupCodesResponse().backupCodes(backupCodes)).build();
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
        User verified = verifyEmailUseCase.verify(req.getEmail(), req.getCode());
        return Response.ok(toAccountResponse(verified)).build();
    }

    @Override
    @PermitAll
    public Response resendVerification(ResendVerificationRequest req) {
        resendVerificationUseCase.resend(req.getEmail());
        return Response.noContent().build();
    }

    @Override
    @RolesAllowed("user")
    public Response getApplyProfile() {
        var profile = getApplyProfileUseCase.get(currentUserId());
        return Response.ok(ApplyProfileMapper.toResponse(profile)).build();
    }

    @Override
    @RolesAllowed("user")
    public Response saveApplyProfile(ApplyProfileRequest req) {
        var saved = saveApplyProfileUseCase.save(currentUserId(), ApplyProfileMapper.toCommand(req));
        return Response.ok(ApplyProfileMapper.toResponse(saved)).build();
    }

    private AccountResponse toAccountResponse(User user) {
        String allowlist = adminEmailsConfig.orElse("");
        boolean isAdmin = AdminAllowlist.isAdmin(allowlist, user.getEmail());
        return AccountResponseMapper.toAccount(user, isAdmin, AdminAllowlist.groupsFor(allowlist, user.getEmail()));
    }

    private UUID currentUserId() {
        return UUID.fromString(jwt.getSubject());
    }
}
