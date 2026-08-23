package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.out.ApplyProfileRepository;
import com.davidcreate.jobhub.auth.application.usecase.GetApplyProfileService;
import com.davidcreate.jobhub.auth.domain.entity.ApplyProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetApplyProfileService Unit Tests")
class GetApplyProfileServiceTest {

    @Mock ApplyProfileRepository applyProfileRepository;
    @InjectMocks GetApplyProfileService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("returns the saved profile when one exists")
    void returnsSavedProfile() {
        ApplyProfile saved = ApplyProfile.builder().userId(userId).workAuthorization("US Citizen").build();
        when(applyProfileRepository.findByUserId(userId)).thenReturn(Optional.of(saved));

        ApplyProfile result = service.get(userId);

        assertThat(result).isEqualTo(saved);
    }

    @Test
    @DisplayName("AC1: returns an all-null empty profile (never 404/exception) when never saved")
    void returnsEmptyProfileWhenNeverSaved() {
        when(applyProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        ApplyProfile result = service.get(userId);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getWorkAuthorization()).isNull();
        assertThat(result.getLanguages()).isNull();
        assertThat(result.getUpdatedAt()).isNull();
    }
}
