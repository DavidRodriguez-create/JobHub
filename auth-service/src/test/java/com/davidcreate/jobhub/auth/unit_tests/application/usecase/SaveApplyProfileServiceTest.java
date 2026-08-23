package com.davidcreate.jobhub.auth.unit_tests.application.usecase;

import com.davidcreate.jobhub.auth.application.port.in.SaveApplyProfileCommand;
import com.davidcreate.jobhub.auth.application.port.out.ApplyProfileRepository;
import com.davidcreate.jobhub.auth.application.usecase.SaveApplyProfileService;
import com.davidcreate.jobhub.auth.domain.entity.ApplyProfile;
import com.davidcreate.jobhub.auth.domain.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SaveApplyProfileService Unit Tests")
class SaveApplyProfileServiceTest {

    @Mock ApplyProfileRepository applyProfileRepository;
    @InjectMocks SaveApplyProfileService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("AC2: first-ever save creates the profile from the empty shape")
    void createsProfileOnFirstSave() {
        when(applyProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(applyProfileRepository.save(any(ApplyProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        SaveApplyProfileCommand command = new SaveApplyProfileCommand(
                "US Citizen", false, "2 weeks", "$120k-$140k", "Madrid, Spain", true,
                "https://linkedin.com/in/alice", "https://github.com/alice", "https://alice.dev",
                List.of("English (native)"), "Grow into a staff role");

        ApplyProfile result = service.save(userId, command);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getWorkAuthorization()).isEqualTo("US Citizen");
        assertThat(result.getLanguages()).containsExactly("English (native)");
    }

    @Test
    @DisplayName("AC4: editing one field on an existing profile preserves the rest")
    void editingOneFieldPreservesRest() {
        ApplyProfile existing = ApplyProfile.builder()
                .userId(userId)
                .workAuthorization("US Citizen")
                .noticePeriod("2 weeks")
                .salaryExpectation("$100k")
                .currentLocation("Madrid, Spain")
                .build();
        when(applyProfileRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(applyProfileRepository.save(any(ApplyProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        SaveApplyProfileCommand command = new SaveApplyProfileCommand(
                "US Citizen", null, "1 month", "$100k", "Madrid, Spain", null,
                null, null, null, null, null);

        service.save(userId, command);

        ArgumentCaptor<ApplyProfile> captor = ArgumentCaptor.forClass(ApplyProfile.class);
        verify(applyProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getNoticePeriod()).isEqualTo("1 month");
        assertThat(captor.getValue().getWorkAuthorization()).isEqualTo("US Citizen");
        assertThat(captor.getValue().getCurrentLocation()).isEqualTo("Madrid, Spain");
    }

    @Test
    @DisplayName("BR-5/BR-6: an invalid command throws before the repository is ever called (atomicity)")
    void invalidCommandNeverReachesRepository() {
        ApplyProfile existing = ApplyProfile.builder().userId(userId).workAuthorization("US Citizen").build();
        when(applyProfileRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

        SaveApplyProfileCommand command = new SaveApplyProfileCommand(
                "US Citizen", null, null, null, null, null,
                null, null, null, null, "x".repeat(2001));

        assertThatThrownBy(() -> service.save(userId, command)).isInstanceOf(ValidationException.class);

        verify(applyProfileRepository, never()).save(any());
    }
}
