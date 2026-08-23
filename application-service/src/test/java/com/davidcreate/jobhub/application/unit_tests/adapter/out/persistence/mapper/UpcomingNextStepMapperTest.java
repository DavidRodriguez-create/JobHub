package com.davidcreate.jobhub.application.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.application.adapter.out.persistence.mapper.UpcomingNextStepMapper;
import com.davidcreate.jobhub.application.contract.model.UpcomingNextStepItem;
import com.davidcreate.jobhub.application.contract.model.UpcomingNextStepsResponse;
import com.davidcreate.jobhub.application.domain.entity.UpcomingNextStep;
import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UpcomingNextStepMapper")
class UpcomingNextStepMapperTest {

    @Test
    @DisplayName("TC-141a: maps a row field-for-field to UpcomingNextStepItem")
    void mapsEntityRowToResponseItem() {
        UUID userId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        LocalDate nextStepDate = LocalDate.of(2026, 6, 17);

        UpcomingNextStep row = UpcomingNextStep.builder()
                .userId(userId)
                .applicationId(applicationId)
                .nextStepLabel("Interview with Product Manager")
                .nextStepDate(nextStepDate)
                .nextStepReminderAt(null)
                .companyName("Acme Corp")
                .status(ApplicationStatus.INTERVIEWING)
                .build();

        UpcomingNextStepItem item = UpcomingNextStepMapper.toResponseItem(row);

        assertThat(item.getUserId()).isEqualTo(userId);
        assertThat(item.getApplicationId()).isEqualTo(applicationId);
        assertThat(item.getNextStepLabel()).isEqualTo("Interview with Product Manager");
        assertThat(item.getNextStepDate()).isEqualTo(nextStepDate);
        assertThat(item.getNextStepReminderAt()).isNull();
        assertThat(item.getCompanyName()).isEqualTo("Acme Corp");
        assertThat(item.getStatus()).isEqualTo(com.davidcreate.jobhub.application.contract.model.ApplicationStatus.INTERVIEWING);
    }

    @Test
    @DisplayName("TC-134: maps null companyName to null (not empty string, not literal 'null')")
    void mapsNullCompanyToNullCompanyName() {
        UpcomingNextStep row = UpcomingNextStep.builder()
                .userId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .nextStepLabel("Interview with Product Manager")
                .nextStepDate(LocalDate.of(2026, 6, 17))
                .nextStepReminderAt(null)
                .companyName(null)
                .status(ApplicationStatus.INTERVIEWING)
                .build();

        UpcomingNextStepItem item = UpcomingNextStepMapper.toResponseItem(row);

        assertThat(item.getCompanyName()).isNull();
    }

    @Test
    @DisplayName("maps an empty list of rows to an UpcomingNextStepsResponse with empty items")
    void mapsEmptyListToEmptyResponse() {
        UpcomingNextStepsResponse response = UpcomingNextStepMapper.toResponse(List.of());

        assertThat(response.getItems()).isNotNull();
        assertThat(response.getItems()).isEmpty();
    }

    @Test
    @DisplayName("maps a list of rows to an UpcomingNextStepsResponse with matching items")
    void mapsListToResponseWithItems() {
        UpcomingNextStep row = UpcomingNextStep.builder()
                .userId(UUID.randomUUID())
                .applicationId(UUID.randomUUID())
                .nextStepLabel("Onsite interview")
                .nextStepDate(LocalDate.of(2026, 6, 18))
                .nextStepReminderAt(null)
                .companyName(null)
                .status(ApplicationStatus.INTERVIEWING)
                .build();

        UpcomingNextStepsResponse response = UpcomingNextStepMapper.toResponse(List.of(row));

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getNextStepLabel()).isEqualTo("Onsite interview");
    }
}
