package com.davidcreate.jobhub.application.unit_tests.application.usecase;

import com.davidcreate.jobhub.application.application.port.out.UpcomingNextStepRepository;
import com.davidcreate.jobhub.application.application.usecase.GetUpcomingNextStepsHandler;
import com.davidcreate.jobhub.application.domain.entity.UpcomingNextStep;
import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetUpcomingNextStepsHandler")
class GetUpcomingNextStepsHandlerTest {

    @Mock
    UpcomingNextStepRepository repository;

    @InjectMocks
    GetUpcomingNextStepsHandler handler;

    @Test
    @DisplayName("TC-135: passes withinHours through to the repository query")
    void passesWithinHoursThroughToRepositoryQuery() {
        when(repository.findUpcoming(26)).thenReturn(List.of());

        handler.handle(26);

        verify(repository).findUpcoming(26);
    }

    @Test
    @DisplayName("TC-135: does not substitute a different default when an explicit value is supplied")
    void doesNotSubstituteDifferentValue() {
        when(repository.findUpcoming(48)).thenReturn(List.of());

        handler.handle(48);

        verify(repository).findUpcoming(48);
    }

    @Test
    @DisplayName("TC-136: returns empty items (not null) when the repository returns empty")
    void returnsEmptyItemsWhenRepositoryReturnsEmpty() {
        when(repository.findUpcoming(26)).thenReturn(List.of());

        List<UpcomingNextStep> result = handler.handle(26);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("returns the repository's result unchanged when items exist")
    void returnsRepositoryResultWhenItemsExist() {
        UpcomingNextStep item = UpcomingNextStep.builder()
                .userId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .nextStepLabel("Interview with Product Manager")
                .nextStepDate(LocalDate.now().plusDays(1))
                .nextStepReminderAt(null)
                .companyName("Acme Corp")
                .status(ApplicationStatus.INTERVIEWING)
                .build();
        when(repository.findUpcoming(26)).thenReturn(List.of(item));

        List<UpcomingNextStep> result = handler.handle(26);

        assertThat(result).containsExactly(item);
    }
}
