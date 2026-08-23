package com.davidcreate.jobhub.auth.application.port.in;

import java.util.List;

public record SaveApplyProfileCommand(
        String workAuthorization,
        Boolean requiresSponsorship,
        String noticePeriod,
        String salaryExpectation,
        String currentLocation,
        Boolean willingToRelocate,
        String linkedinUrl,
        String githubUrl,
        String portfolioUrl,
        List<String> languages,
        String roomToGrow) {
}
