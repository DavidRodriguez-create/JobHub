package com.davidcreate.jobhub.auth.adapter.in.rest.dto;

import com.davidcreate.jobhub.auth.application.port.in.SaveApplyProfileCommand;
import com.davidcreate.jobhub.auth.contract.model.ApplyProfileRequest;
import com.davidcreate.jobhub.auth.contract.model.ApplyProfileResponse;
import com.davidcreate.jobhub.auth.domain.entity.ApplyProfile;

import java.net.URI;

/**
 * Maps between the frozen contract's {@code ApplyProfileRequest}/{@code ApplyProfileResponse}
 * and the {@code SaveApplyProfileCommand} / domain {@code ApplyProfile}. The three URL fields
 * are generated as {@code java.net.URI} on the request (contract uses {@code format: uri}); the
 * command/domain keep them as plain {@code String} (see ADR 0022 / ticket #421 handoff note on
 * the generator's {@code format: uri} + {@code maxLength} combination).
 */
public final class ApplyProfileMapper {

    private ApplyProfileMapper() {
    }

    public static SaveApplyProfileCommand toCommand(ApplyProfileRequest req) {
        return new SaveApplyProfileCommand(
                req.getWorkAuthorization(),
                req.getRequiresSponsorship(),
                req.getNoticePeriod(),
                req.getSalaryExpectation(),
                req.getCurrentLocation(),
                req.getWillingToRelocate(),
                toUriString(req.getLinkedinUrl()),
                toUriString(req.getGithubUrl()),
                toUriString(req.getPortfolioUrl()),
                req.getLanguages(),
                req.getRoomToGrow());
    }

    public static ApplyProfileResponse toResponse(ApplyProfile profile) {
        return new ApplyProfileResponse()
                .workAuthorization(profile.getWorkAuthorization())
                .requiresSponsorship(profile.getRequiresSponsorship())
                .noticePeriod(profile.getNoticePeriod())
                .salaryExpectation(profile.getSalaryExpectation())
                .currentLocation(profile.getCurrentLocation())
                .willingToRelocate(profile.getWillingToRelocate())
                .linkedinUrl(profile.getLinkedinUrl())
                .githubUrl(profile.getGithubUrl())
                .portfolioUrl(profile.getPortfolioUrl())
                .languages(profile.getLanguages())
                .roomToGrow(profile.getRoomToGrow())
                .updatedAt(profile.getUpdatedAt());
    }

    private static String toUriString(URI uri) {
        return uri == null ? null : uri.toString();
    }
}
