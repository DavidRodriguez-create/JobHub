package com.davidcreate.jobhub.auth.unit_tests.adapter.in.rest.dto;

import com.davidcreate.jobhub.auth.adapter.in.rest.dto.ApplyProfileMapper;
import com.davidcreate.jobhub.auth.application.port.in.SaveApplyProfileCommand;
import com.davidcreate.jobhub.auth.contract.model.ApplyProfileRequest;
import com.davidcreate.jobhub.auth.contract.model.ApplyProfileResponse;
import com.davidcreate.jobhub.auth.domain.entity.ApplyProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BE-U6: request DTO maps 1:1 into the SaveApplyProfileCommand.
 * BE-U7: response mapping never leaks account identity fields (AC13).
 */
@DisplayName("ApplyProfileMapper (REST DTO) Unit Tests — BE-U6/BE-U7")
class ApplyProfileMapperTest {

    @Test
    @DisplayName("BE-U6: every field on a fully-populated request maps unchanged into the command")
    void requestMapsOneToOneIntoCommand() {
        ApplyProfileRequest req = new ApplyProfileRequest()
                .workAuthorization("US Citizen")
                .requiresSponsorship(false)
                .noticePeriod("2 weeks")
                .salaryExpectation("$120k-$140k")
                .currentLocation("Madrid, Spain")
                .willingToRelocate(true)
                .linkedinUrl(URI.create("https://linkedin.com/in/alice"))
                .githubUrl(URI.create("https://github.com/alice"))
                .portfolioUrl(URI.create("https://alice.dev"))
                .languages(List.of("English (native)", "Spanish (C1)"))
                .roomToGrow("Grow into a staff engineer role");

        SaveApplyProfileCommand command = ApplyProfileMapper.toCommand(req);

        assertThat(command.workAuthorization()).isEqualTo("US Citizen");
        assertThat(command.requiresSponsorship()).isFalse();
        assertThat(command.noticePeriod()).isEqualTo("2 weeks");
        assertThat(command.salaryExpectation()).isEqualTo("$120k-$140k");
        assertThat(command.currentLocation()).isEqualTo("Madrid, Spain");
        assertThat(command.willingToRelocate()).isTrue();
        assertThat(command.linkedinUrl()).isEqualTo("https://linkedin.com/in/alice");
        assertThat(command.githubUrl()).isEqualTo("https://github.com/alice");
        assertThat(command.portfolioUrl()).isEqualTo("https://alice.dev");
        assertThat(command.languages()).containsExactly("English (native)", "Spanish (C1)");
        assertThat(command.roomToGrow()).isEqualTo("Grow into a staff engineer role");
    }

    @Test
    @DisplayName("BE-U6: null request fields map to null command fields (no defaulting)")
    void nullRequestFieldsMapToNullCommandFields() {
        ApplyProfileRequest req = new ApplyProfileRequest();

        SaveApplyProfileCommand command = ApplyProfileMapper.toCommand(req);

        assertThat(command.workAuthorization()).isNull();
        assertThat(command.requiresSponsorship()).isNull();
        assertThat(command.linkedinUrl()).isNull();
        assertThat(command.languages()).isNull();
    }

    @Test
    @DisplayName("BE-U7: response mapping maps all 11 answer fields + updatedAt 1:1")
    void responseMapsAllFieldsOneToOne() {
        OffsetDateTime updatedAt = OffsetDateTime.now();
        ApplyProfile profile = ApplyProfile.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .workAuthorization("US Citizen")
                .requiresSponsorship(false)
                .noticePeriod(null)
                .salaryExpectation("$120k-$140k")
                .currentLocation(null)
                .willingToRelocate(true)
                .linkedinUrl("https://linkedin.com/in/alice")
                .githubUrl(null)
                .portfolioUrl("https://alice.dev")
                .languages(List.of("English (native)"))
                .roomToGrow(null)
                .updatedAt(updatedAt)
                .build();

        ApplyProfileResponse response = ApplyProfileMapper.toResponse(profile);

        assertThat(response.getWorkAuthorization()).isEqualTo("US Citizen");
        assertThat(response.getRequiresSponsorship()).isFalse();
        assertThat(response.getNoticePeriod()).isNull();
        assertThat(response.getSalaryExpectation()).isEqualTo("$120k-$140k");
        assertThat(response.getCurrentLocation()).isNull();
        assertThat(response.getWillingToRelocate()).isTrue();
        assertThat(response.getLinkedinUrl()).isEqualTo("https://linkedin.com/in/alice");
        assertThat(response.getGithubUrl()).isNull();
        assertThat(response.getPortfolioUrl()).isEqualTo("https://alice.dev");
        assertThat(response.getLanguages()).containsExactly("English (native)");
        assertThat(response.getRoomToGrow()).isNull();
        assertThat(response.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("BE-U7 (AC13): the serialized ApplyProfileResponse never carries account identity fields")
    void responseNeverLeaksAccountIdentityFields() throws Exception {
        ApplyProfile profile = ApplyProfile.builder()
                .userId(UUID.randomUUID())
                .workAuthorization("US Citizen")
                .updatedAt(OffsetDateTime.now())
                .build();

        ApplyProfileResponse response = ApplyProfileMapper.toResponse(profile);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        Map<String, Object> serialized = objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {
        });

        assertThat(serialized).doesNotContainKeys("firstName", "lastName", "email", "emailVerified", "id");
    }
}
