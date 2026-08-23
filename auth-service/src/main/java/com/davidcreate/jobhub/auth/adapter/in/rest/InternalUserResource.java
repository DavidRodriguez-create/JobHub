package com.davidcreate.jobhub.auth.adapter.in.rest;

import com.davidcreate.jobhub.auth.application.port.in.GetTwoFactorStatusUseCase;
import com.davidcreate.jobhub.auth.application.port.in.GetUserEmailsUseCase;
import com.davidcreate.jobhub.auth.application.port.in.UserEmailResult;
import com.davidcreate.jobhub.auth.application.port.in.VerifyTwoFactorForServiceCommand;
import com.davidcreate.jobhub.auth.application.port.in.VerifyTwoFactorForServiceUseCase;
import com.davidcreate.jobhub.auth.application.port.in.VerifyTwoFactorOutcome;
import com.davidcreate.jobhub.auth.application.port.out.UserRepository;
import com.davidcreate.jobhub.auth.contract.api.InternalApi;
import com.davidcreate.jobhub.auth.contract.model.TwoFactorStatusResponse;
import com.davidcreate.jobhub.auth.contract.model.UserEmailBatchResponse;
import com.davidcreate.jobhub.auth.contract.model.UserEmailEntry;
import com.davidcreate.jobhub.auth.contract.model.UsersWithoutTwoFactorResponse;
import com.davidcreate.jobhub.auth.contract.model.VerifyTwoFactorRequest;
import com.davidcreate.jobhub.auth.contract.model.VerifyTwoFactorResponse;
import com.davidcreate.jobhub.auth.domain.exception.ValidationException;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
@Path("/internal")
@RequiredArgsConstructor
public class InternalUserResource implements InternalApi {

    private static final int MAX_USER_IDS = 500;

    // Mirrors VerifyTwoFactorRequest.code's contract pattern. Validated explicitly here
    // (like getUserEmails does for userIds above) rather than relying solely on the
    // generated model's @Pattern/@NotNull annotations, which are declared on the
    // InternalApi interface method and are not reliably enforced against the concrete
    // override in this resource.
    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$|^[a-zA-Z0-9]{8}$");

    private final GetUserEmailsUseCase getUserEmailsUseCase;
    private final UserRepository userRepository;
    private final GetTwoFactorStatusUseCase getTwoFactorStatusUseCase;
    private final VerifyTwoFactorForServiceUseCase verifyTwoFactorForServiceUseCase;

    @Override
    @PermitAll
    public Response getUserEmails(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new ValidationException("userIds query parameter is required and must contain at least one value");
        }
        if (userIds.size() > MAX_USER_IDS) {
            throw new ValidationException("userIds must contain at most " + MAX_USER_IDS + " values");
        }

        List<UserEmailEntry> entries = getUserEmailsUseCase.getEmails(userIds).stream()
                .map(InternalUserResource::toEntry)
                .toList();

        return Response.ok(new UserEmailBatchResponse().emails(entries)).build();
    }

    @Override
    @PermitAll
    public Response getUsersWithoutTwoFactor(Integer sinceDays) {
        int days = sinceDays == null ? 1 : sinceDays;
        OffsetDateTime since = OffsetDateTime.now().minusDays(days);
        List<UUID> userIds = userRepository.findUserIdsWithoutTwoFactorSince(since);
        return Response.ok(new UsersWithoutTwoFactorResponse().userIds(userIds)).build();
    }

    @Override
    @PermitAll
    public Response getUserTwoFactorStatus(UUID userId) {
        boolean enabled = getTwoFactorStatusUseCase.getStatus(userId);
        return Response.ok(new TwoFactorStatusResponse().twoFactorEnabled(enabled)).build();
    }

    @Override
    @PermitAll
    public Response verifyUserTwoFactor(VerifyTwoFactorRequest req) {
        if (req == null || req.getUserId() == null) {
            throw new ValidationException("userId is required");
        }
        if (req.getCode() != null && !CODE_PATTERN.matcher(req.getCode()).matches()) {
            throw new ValidationException("code must be a 6-digit TOTP code or an 8-character backup code");
        }

        VerifyTwoFactorOutcome outcome = verifyTwoFactorForServiceUseCase.verify(
                new VerifyTwoFactorForServiceCommand(req.getUserId(), req.getCode()));
        return Response.ok(new VerifyTwoFactorResponse().outcome(toContractOutcome(outcome))).build();
    }

    private static VerifyTwoFactorResponse.OutcomeEnum toContractOutcome(VerifyTwoFactorOutcome outcome) {
        return outcome == VerifyTwoFactorOutcome.VERIFIED
                ? VerifyTwoFactorResponse.OutcomeEnum.VERIFIED
                : VerifyTwoFactorResponse.OutcomeEnum.NOT_ENROLLED;
    }

    private static UserEmailEntry toEntry(UserEmailResult result) {
        return new UserEmailEntry().userId(result.userId()).email(result.email());
    }
}
