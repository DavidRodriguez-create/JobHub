package com.davidcreate.jobhub.auth.domain.entity;

import com.davidcreate.jobhub.auth.domain.exception.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The authenticated user's apply-profile answer bank (story #336, ADR 0022): a single,
 * per-user, full-replace record of the recurring answers external ATS forms ask for.
 * Every field is independently optional; {@link #replace} normalizes blank text and
 * empty language lists to {@code null} so "never answered" has one stable shape.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class ApplyProfile {

    private static final int WORK_AUTHORIZATION_MAX = 200;
    private static final int NOTICE_PERIOD_MAX = 100;
    private static final int SALARY_EXPECTATION_MAX = 100;
    private static final int CURRENT_LOCATION_MAX = 200;
    private static final int URL_MAX = 500;
    private static final int ROOM_TO_GROW_MAX = 2000;
    private static final int LANGUAGE_MAX = 60;
    private static final int LANGUAGES_MAX_ITEMS = 20;

    private final UUID id;
    private final UUID userId;
    private final String workAuthorization;
    private final Boolean requiresSponsorship;
    private final String noticePeriod;
    private final String salaryExpectation;
    private final String currentLocation;
    private final Boolean willingToRelocate;
    private final String linkedinUrl;
    private final String githubUrl;
    private final String portfolioUrl;
    private final List<String> languages;
    private final String roomToGrow;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    public static ApplyProfile empty(UUID userId) {
        return ApplyProfile.builder().userId(userId).build();
    }

    /**
     * Full-replace upsert (BR-2): every field is overwritten with the given value,
     * normalizing blank text and an empty language list to {@code null} (AC5/AC6).
     * Validates length/count caps (BR-6) before returning, so an invalid submission
     * never mutates {@code this} (BR-5 atomicity is the caller's responsibility: build
     * the replacement first, persist only if this call does not throw).
     */
    public ApplyProfile replace(String workAuthorization, Boolean requiresSponsorship, String noticePeriod,
            String salaryExpectation, String currentLocation, Boolean willingToRelocate,
            String linkedinUrl, String githubUrl, String portfolioUrl,
            List<String> languages, String roomToGrow) {

        String normWorkAuthorization = normalizeText(workAuthorization);
        String normNoticePeriod = normalizeText(noticePeriod);
        String normSalaryExpectation = normalizeText(salaryExpectation);
        String normCurrentLocation = normalizeText(currentLocation);
        String normLinkedinUrl = normalizeText(linkedinUrl);
        String normGithubUrl = normalizeText(githubUrl);
        String normPortfolioUrl = normalizeText(portfolioUrl);
        List<String> normLanguages = normalizeLanguages(languages);
        String normRoomToGrow = normalizeText(roomToGrow);

        validateLength("workAuthorization", normWorkAuthorization, WORK_AUTHORIZATION_MAX);
        validateLength("noticePeriod", normNoticePeriod, NOTICE_PERIOD_MAX);
        validateLength("salaryExpectation", normSalaryExpectation, SALARY_EXPECTATION_MAX);
        validateLength("currentLocation", normCurrentLocation, CURRENT_LOCATION_MAX);
        validateLength("linkedinUrl", normLinkedinUrl, URL_MAX);
        validateLength("githubUrl", normGithubUrl, URL_MAX);
        validateLength("portfolioUrl", normPortfolioUrl, URL_MAX);
        validateLength("roomToGrow", normRoomToGrow, ROOM_TO_GROW_MAX);
        validateLanguages(normLanguages);

        return this.toBuilder()
                .workAuthorization(normWorkAuthorization)
                .requiresSponsorship(requiresSponsorship)
                .noticePeriod(normNoticePeriod)
                .salaryExpectation(normSalaryExpectation)
                .currentLocation(normCurrentLocation)
                .willingToRelocate(willingToRelocate)
                .linkedinUrl(normLinkedinUrl)
                .githubUrl(normGithubUrl)
                .portfolioUrl(normPortfolioUrl)
                .languages(normLanguages)
                .roomToGrow(normRoomToGrow)
                .build();
    }

    private static String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static List<String> normalizeLanguages(List<String> languages) {
        if (languages == null || languages.isEmpty()) {
            return null;
        }
        return List.copyOf(languages);
    }

    private static void validateLength(String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw new ValidationException(field + " must not exceed " + max + " characters");
        }
    }

    private static void validateLanguages(List<String> languages) {
        if (languages == null) {
            return;
        }
        if (languages.size() > LANGUAGES_MAX_ITEMS) {
            throw new ValidationException("languages must not contain more than " + LANGUAGES_MAX_ITEMS + " entries");
        }
        for (String language : languages) {
            if (language != null && language.length() > LANGUAGE_MAX) {
                throw new ValidationException("languages entries must not exceed " + LANGUAGE_MAX + " characters");
            }
        }
    }
}
