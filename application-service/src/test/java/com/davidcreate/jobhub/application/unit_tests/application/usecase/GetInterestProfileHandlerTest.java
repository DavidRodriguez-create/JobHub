package com.davidcreate.jobhub.application.unit_tests.application.usecase;

import com.davidcreate.jobhub.application.application.port.out.InterestProfileRepository;
import com.davidcreate.jobhub.application.application.usecase.GetInterestProfileHandler;
import com.davidcreate.jobhub.application.domain.entity.InterestProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetInterestProfileHandler")
class GetInterestProfileHandlerTest {

    @Mock
    InterestProfileRepository repository;

    @InjectMocks
    GetInterestProfileHandler handler;

    @Test
    @DisplayName("delegates to the repository and returns its result for a user with history")
    void returnsAggregatedProfile() {
        UUID userId = UUID.randomUUID();
        InterestProfile expected = InterestProfile.builder()
                .userId(userId)
                .locations(List.of("Barcelona, Spain"))
                .companies(List.of("Acme Corp"))
                .keywords(List.of("backend", "java", "developer"))
                .build();
        when(repository.findInterestProfile(userId)).thenReturn(expected);

        InterestProfile result = handler.getInterestProfile(userId);

        assertThat(result).isSameAs(expected);
        verify(repository).findInterestProfile(userId);
    }

    @Test
    @DisplayName("returns empty arrays for a user with no application history")
    void returnsEmptyProfile() {
        UUID userId = UUID.randomUUID();
        InterestProfile empty = InterestProfile.builder()
                .userId(userId)
                .locations(List.of())
                .companies(List.of())
                .keywords(List.of())
                .build();
        when(repository.findInterestProfile(userId)).thenReturn(empty);

        InterestProfile result = handler.getInterestProfile(userId);

        assertThat(result.getLocations()).isEmpty();
        assertThat(result.getCompanies()).isEmpty();
        assertThat(result.getKeywords()).isEmpty();
    }
}
