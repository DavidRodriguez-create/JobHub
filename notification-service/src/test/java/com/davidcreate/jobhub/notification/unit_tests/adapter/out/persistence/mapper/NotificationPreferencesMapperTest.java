package com.davidcreate.jobhub.notification.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.notification.adapter.out.persistence.entity.NotificationPreferencesEntity;
import com.davidcreate.jobhub.notification.adapter.out.persistence.mapper.NotificationPreferencesMapper;
import com.davidcreate.jobhub.notification.domain.model.NotificationPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationPreferencesMapper Unit Tests")
class NotificationPreferencesMapperTest {

    private NotificationPreferencesMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new NotificationPreferencesMapper();
    }

    // TC-06
    @Test
    @DisplayName("toDomain copies every field 1:1; toEntity round-trips id/userId and the four booleans")
    void mapsEntityToDomainAndBack() {
        NotificationPreferencesEntity entity = new NotificationPreferencesEntity();
        entity.id = UUID.randomUUID();
        entity.userId = UUID.randomUUID();
        entity.weeklyDigestEmail = false;
        entity.inAppNotificationsEnabled = true;
        entity.interviewReminders = false;
        entity.interviewReminderEmail = true;
        entity.ghostedAlert = true;

        NotificationPreferences domain = mapper.toDomain(entity);

        assertThat(domain.getId()).isEqualTo(entity.id);
        assertThat(domain.getUserId()).isEqualTo(entity.userId);
        assertThat(domain.isWeeklyDigestEmail()).isEqualTo(entity.weeklyDigestEmail);
        assertThat(domain.isInAppNotificationsEnabled()).isEqualTo(entity.inAppNotificationsEnabled);
        assertThat(domain.isInterviewReminders()).isEqualTo(entity.interviewReminders);
        assertThat(domain.isInterviewReminderEmail()).isEqualTo(entity.interviewReminderEmail);
        assertThat(domain.isGhostedAlert()).isEqualTo(entity.ghostedAlert);

        NotificationPreferencesEntity roundTrip = mapper.toEntity(domain);

        assertThat(roundTrip.id).isEqualTo(entity.id);
        assertThat(roundTrip.userId).isEqualTo(entity.userId);
        assertThat(roundTrip.weeklyDigestEmail).isEqualTo(entity.weeklyDigestEmail);
        assertThat(roundTrip.inAppNotificationsEnabled).isEqualTo(entity.inAppNotificationsEnabled);
        assertThat(roundTrip.interviewReminders).isEqualTo(entity.interviewReminders);
        assertThat(roundTrip.interviewReminderEmail).isEqualTo(entity.interviewReminderEmail);
        assertThat(roundTrip.ghostedAlert).isEqualTo(entity.ghostedAlert);
    }
}
