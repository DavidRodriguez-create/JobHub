package com.davidcreate.jobhub.notification.unit_tests.adapter.in.rest.dto;

import com.davidcreate.jobhub.notification.adapter.in.rest.dto.CustomReminderListMapper;
import com.davidcreate.jobhub.notification.adapter.in.rest.dto.CustomReminderResponseMapper;
import com.davidcreate.jobhub.notification.contract.model.CustomReminderResponse;
import com.davidcreate.jobhub.notification.domain.model.CustomReminder;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderChannel;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomReminderResponseMapper / CustomReminderListMapper Unit Tests")
class CustomReminderResponseMapperTest {

    private CustomReminderResponseMapper responseMapper;
    private CustomReminderListMapper listMapper;

    @BeforeEach
    void setUp() {
        responseMapper = new CustomReminderResponseMapper();
        listMapper = new CustomReminderListMapper(responseMapper);
    }

    private CustomReminder fullyPopulated() {
        Instant now = Instant.now();
        return CustomReminder.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .title("Prep call")
                .note("Bring questions")
                .triggerAtUtc(now.plusSeconds(3600))
                .channels(Set.of(CustomReminderChannel.IN_APP))
                .status(CustomReminderStatus.SCHEDULED)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // CR-U-095
    @Test
    @DisplayName("CR-U-095: all required fields present")
    void responseHasAllRequiredFields() {
        CustomReminderResponse response = responseMapper.toResponse(fullyPopulated());

        assertThat(response.getId()).isNotNull();
        assertThat(response.getApplicationId()).isNotNull();
        assertThat(response.getTitle()).isNotNull();
        assertThat(response.getTriggerAtUtc()).isNotNull();
        assertThat(response.getChannels()).isNotEmpty();
        assertThat(response.getStatus()).isNotNull();
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isNotNull();
    }

    // CR-U-096
    @Test
    @DisplayName("CR-U-096: channels {IN_APP} maps to response list [\"IN_APP\"]")
    void channelsMapCorrectly() {
        CustomReminder domain = fullyPopulated().toBuilder().channels(Set.of(CustomReminderChannel.IN_APP)).build();

        CustomReminderResponse response = responseMapper.toResponse(domain);

        assertThat(response.getChannels()).containsExactly(
                com.davidcreate.jobhub.notification.contract.model.CustomReminderChannel.IN_APP);
    }

    // CR-U-097
    @Test
    @DisplayName("CR-U-097: domain list of 3 maps to CustomReminderList.content size 3")
    void listMapperReturnsCorrectSize() {
        List<CustomReminder> domainList = List.of(fullyPopulated(), fullyPopulated(), fullyPopulated());

        var result = listMapper.toList(domainList);

        assertThat(result.getContent()).hasSize(3);
    }
}
