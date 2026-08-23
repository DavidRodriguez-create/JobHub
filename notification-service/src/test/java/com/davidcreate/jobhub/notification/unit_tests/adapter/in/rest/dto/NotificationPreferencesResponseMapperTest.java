package com.davidcreate.jobhub.notification.unit_tests.adapter.in.rest.dto;

import com.davidcreate.jobhub.notification.adapter.in.rest.dto.NotificationPreferencesResponseMapper;
import com.davidcreate.jobhub.notification.contract.model.NotificationPreferencesResponse;
import com.davidcreate.jobhub.notification.domain.model.NotificationPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationPreferencesResponseMapper Unit Tests")
class NotificationPreferencesResponseMapperTest {

    private NotificationPreferencesResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new NotificationPreferencesResponseMapper();
    }

    // TC-07
    @Test
    @DisplayName("toResponse copies the four boolean fields and leaks no id/userId")
    void mapsDomainToResponse() {
        NotificationPreferences domain = NotificationPreferences.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .weeklyDigestEmail(false)
                .inAppNotificationsEnabled(true)
                .interviewReminders(false)
                .ghostedAlert(true)
                .build();

        NotificationPreferencesResponse response = mapper.toResponse(domain);

        assertThat(response.getWeeklyDigestEmail()).isFalse();
        assertThat(response.getInAppNotificationsEnabled()).isTrue();
        assertThat(response.getInterviewReminders()).isFalse();
        assertThat(response.getGhostedAlert()).isTrue();
    }

    // CR-153-U regression: toResponse must also copy interviewReminderEmail (it was
    // silently omitted, the fourth hole in #153 beyond ADR 0011 section 11's three).
    @Test
    @DisplayName("CR-153: toResponse copies interviewReminderEmail (true and false)")
    void mapsDomainToResponseCopiesInterviewReminderEmail() {
        NotificationPreferences domainTrue = NotificationPreferences.builder()
                .userId(UUID.randomUUID())
                .interviewReminderEmail(true)
                .build();
        NotificationPreferences domainFalse = NotificationPreferences.builder()
                .userId(UUID.randomUUID())
                .interviewReminderEmail(false)
                .build();

        assertThat(mapper.toResponse(domainTrue).getInterviewReminderEmail()).isTrue();
        assertThat(mapper.toResponse(domainFalse).getInterviewReminderEmail()).isFalse();
    }
}
