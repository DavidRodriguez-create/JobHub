package com.davidcreate.jobhub.auth.unit_tests.domain.entity;

import com.davidcreate.jobhub.auth.domain.entity.ApplyProfile;
import com.davidcreate.jobhub.auth.domain.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ApplyProfile domain entity Unit Tests (BE-U1..BE-U4)")
class ApplyProfileTest {

    private final UUID userId = UUID.randomUUID();

    private ApplyProfile fullProfile() {
        return ApplyProfile.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .workAuthorization("US Citizen")
                .requiresSponsorship(false)
                .noticePeriod("2 weeks")
                .salaryExpectation("$120k-$140k")
                .currentLocation("Madrid, Spain")
                .willingToRelocate(true)
                .linkedinUrl("https://linkedin.com/in/alice")
                .githubUrl("https://github.com/alice")
                .portfolioUrl("https://alice.dev")
                .languages(List.of("English (native)", "Spanish (C1)"))
                .roomToGrow("Grow into a staff engineer role")
                .build();
    }

    private ApplyProfile replaceAllValid(ApplyProfile profile) {
        return profile.replace("US Citizen", false, "2 weeks", "$120k-$140k", "Madrid, Spain", true,
                "https://linkedin.com/in/alice", "https://github.com/alice", "https://alice.dev",
                List.of("English (native)", "Spanish (C1)"), "Grow into a staff engineer role");
    }

    // --- BE-U1: blank single-value text field normalizes to null (AC5) ---

    @ParameterizedTest(name = "blank {0} normalizes to null, other fields unchanged")
    @ValueSource(strings = {"", "   "})
    @DisplayName("BE-U1: blank workAuthorization normalizes to null, rest unchanged")
    void blankWorkAuthorizationNormalizesToNull(String blank) {
        ApplyProfile original = fullProfile();

        ApplyProfile result = original.replace(blank, false, "2 weeks", "$120k-$140k", "Madrid, Spain", true,
                "https://linkedin.com/in/alice", "https://github.com/alice", "https://alice.dev",
                List.of("English (native)", "Spanish (C1)"), "Grow into a staff engineer role");

        assertThat(result.getWorkAuthorization()).isNull();
        assertThat(result.getNoticePeriod()).isEqualTo("2 weeks");
        assertThat(result.getSalaryExpectation()).isEqualTo("$120k-$140k");
        assertThat(result.getCurrentLocation()).isEqualTo("Madrid, Spain");
        assertThat(result.getRoomToGrow()).isEqualTo("Grow into a staff engineer role");
    }

    @ParameterizedTest(name = "blank {0} normalizes to null, other fields unchanged")
    @ValueSource(strings = {"", "  "})
    @DisplayName("BE-U1: blank noticePeriod normalizes to null, rest unchanged")
    void blankNoticePeriodNormalizesToNull(String blank) {
        ApplyProfile original = fullProfile();

        ApplyProfile result = original.replace("US Citizen", false, blank, "$120k-$140k", "Madrid, Spain", true,
                "https://linkedin.com/in/alice", "https://github.com/alice", "https://alice.dev",
                List.of("English (native)", "Spanish (C1)"), "Grow into a staff engineer role");

        assertThat(result.getNoticePeriod()).isNull();
        assertThat(result.getWorkAuthorization()).isEqualTo("US Citizen");
        assertThat(result.getSalaryExpectation()).isEqualTo("$120k-$140k");
    }

    @Test
    @DisplayName("BE-U1: blank salaryExpectation normalizes to null, rest unchanged")
    void blankSalaryExpectationNormalizesToNull() {
        ApplyProfile original = fullProfile();

        ApplyProfile result = original.replace("US Citizen", false, "2 weeks", "", "Madrid, Spain", true,
                "https://linkedin.com/in/alice", "https://github.com/alice", "https://alice.dev",
                List.of("English (native)", "Spanish (C1)"), "Grow into a staff engineer role");

        assertThat(result.getSalaryExpectation()).isNull();
        assertThat(result.getCurrentLocation()).isEqualTo("Madrid, Spain");
    }

    @Test
    @DisplayName("BE-U1: blank currentLocation normalizes to null, rest unchanged")
    void blankCurrentLocationNormalizesToNull() {
        ApplyProfile original = fullProfile();

        ApplyProfile result = original.replace("US Citizen", false, "2 weeks", "$120k-$140k", "   ", true,
                "https://linkedin.com/in/alice", "https://github.com/alice", "https://alice.dev",
                List.of("English (native)", "Spanish (C1)"), "Grow into a staff engineer role");

        assertThat(result.getCurrentLocation()).isNull();
        assertThat(result.getSalaryExpectation()).isEqualTo("$120k-$140k");
    }

    @Test
    @DisplayName("BE-U1: blank roomToGrow normalizes to null, rest unchanged")
    void blankRoomToGrowNormalizesToNull() {
        ApplyProfile original = fullProfile();

        ApplyProfile result = original.replace("US Citizen", false, "2 weeks", "$120k-$140k", "Madrid, Spain", true,
                "https://linkedin.com/in/alice", "https://github.com/alice", "https://alice.dev",
                List.of("English (native)", "Spanish (C1)"), "");

        assertThat(result.getRoomToGrow()).isNull();
        assertThat(result.getWorkAuthorization()).isEqualTo("US Citizen");
    }

    // --- BE-U2: blanking every field returns the entity to the all-null shape (AC6) ---

    @Test
    @DisplayName("BE-U2: blanking every field + empty languages -> all-null entity")
    void blankingEveryFieldReturnsAllNullShape() {
        ApplyProfile original = fullProfile();

        ApplyProfile result = original.replace(null, null, "", null, "  ", null,
                null, "", null, List.of(), null);

        assertThat(result.getWorkAuthorization()).isNull();
        assertThat(result.getRequiresSponsorship()).isNull();
        assertThat(result.getNoticePeriod()).isNull();
        assertThat(result.getSalaryExpectation()).isNull();
        assertThat(result.getCurrentLocation()).isNull();
        assertThat(result.getWillingToRelocate()).isNull();
        assertThat(result.getLinkedinUrl()).isNull();
        assertThat(result.getGithubUrl()).isNull();
        assertThat(result.getPortfolioUrl()).isNull();
        assertThat(result.getLanguages()).isNull();
        assertThat(result.getRoomToGrow()).isNull();
    }

    // --- BE-U3: empty languages list normalizes the same as null (AC6) ---

    @Test
    @DisplayName("BE-U3: empty languages list normalizes to null, same as passing null")
    void emptyLanguagesListNormalizesToNull() {
        ApplyProfile original = fullProfile();

        ApplyProfile withEmptyList = replaceAllValidExceptLanguages(original, List.of());
        ApplyProfile withNull = replaceAllValidExceptLanguages(original, null);

        assertThat(withEmptyList.getLanguages()).isNull();
        assertThat(withNull.getLanguages()).isNull();
    }

    private ApplyProfile replaceAllValidExceptLanguages(ApplyProfile profile, List<String> languages) {
        return profile.replace("US Citizen", false, "2 weeks", "$120k-$140k", "Madrid, Spain", true,
                "https://linkedin.com/in/alice", "https://github.com/alice", "https://alice.dev",
                languages, "Grow into a staff engineer role");
    }

    // --- BE-U4: boolean fields pass through explicit values, no false-defaulting ---

    @Test
    @DisplayName("BE-U4: explicit false booleans are preserved as false, not defaulted")
    void explicitFalseBooleansPreserved() {
        ApplyProfile original = fullProfile().toBuilder()
                .requiresSponsorship(true)
                .willingToRelocate(true)
                .build();

        ApplyProfile result = original.replace("US Citizen", false, "2 weeks", "$120k-$140k", "Madrid, Spain", false,
                "https://linkedin.com/in/alice", "https://github.com/alice", "https://alice.dev",
                List.of("English (native)"), "Grow into a staff engineer role");

        assertThat(result.getRequiresSponsorship()).isFalse();
        assertThat(result.getWillingToRelocate()).isFalse();
    }

    @Test
    @DisplayName("BE-U4: null booleans stay null, never silently coerced to false")
    void nullBooleansStayNull() {
        ApplyProfile original = fullProfile().toBuilder()
                .requiresSponsorship(true)
                .willingToRelocate(true)
                .build();

        ApplyProfile result = original.replace("US Citizen", null, "2 weeks", "$120k-$140k", "Madrid, Spain", null,
                "https://linkedin.com/in/alice", "https://github.com/alice", "https://alice.dev",
                List.of("English (native)"), "Grow into a staff engineer role");

        assertThat(result.getRequiresSponsorship()).isNull();
        assertThat(result.getWillingToRelocate()).isNull();
    }

    // --- BR-6: length/count caps enforced by replace() (backs BE-C7..BE-C9 atomicity) ---

    @Test
    @DisplayName("BR-6: over-length roomToGrow (2001 chars) throws ValidationException")
    void overLengthRoomToGrowThrows() {
        ApplyProfile original = fullProfile();

        assertThatThrownBy(() -> original.replace("US Citizen", false, "2 weeks", "$120k-$140k",
                "Madrid, Spain", true, "https://linkedin.com/in/alice", "https://github.com/alice",
                "https://alice.dev", List.of("English"), "x".repeat(2001)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("roomToGrow");
    }

    @Test
    @DisplayName("BR-6: more than 20 languages throws ValidationException")
    void tooManyLanguagesThrows() {
        ApplyProfile original = fullProfile();
        List<String> languages = java.util.stream.IntStream.range(0, 21)
                .mapToObj(i -> "Lang" + i)
                .toList();

        assertThatThrownBy(() -> original.replace("US Citizen", false, "2 weeks", "$120k-$140k",
                "Madrid, Spain", true, "https://linkedin.com/in/alice", "https://github.com/alice",
                "https://alice.dev", languages, "Grow"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("languages");
    }

    @Test
    @DisplayName("BR-6: a single over-length language entry throws ValidationException independently of count")
    void overLengthLanguageEntryThrows() {
        ApplyProfile original = fullProfile();
        List<String> languages = List.of("English", "Spanish", "x".repeat(61), "French", "German");

        assertThatThrownBy(() -> original.replace("US Citizen", false, "2 weeks", "$120k-$140k",
                "Madrid, Spain", true, "https://linkedin.com/in/alice", "https://github.com/alice",
                "https://alice.dev", languages, "Grow"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("languages");
    }

    @Test
    @DisplayName("empty(userId) returns an all-null profile for the given user")
    void emptyReturnsAllNullProfile() {
        ApplyProfile empty = ApplyProfile.empty(userId);

        assertThat(empty.getUserId()).isEqualTo(userId);
        assertThat(empty.getWorkAuthorization()).isNull();
        assertThat(empty.getLanguages()).isNull();
        assertThat(empty.getUpdatedAt()).isNull();
    }
}
