package com.davidcreate.jobhub.notification.unit_tests.adapter.out.client.application;

import com.davidcreate.jobhub.application.contract.model.ApplicationStatus;
import com.davidcreate.jobhub.application.contract.model.UpcomingNextStepItem;
import com.davidcreate.jobhub.application.contract.model.UpcomingNextStepsResponse;
import com.davidcreate.jobhub.notification.adapter.out.client.application.AppInternalRestClient;
import com.davidcreate.jobhub.notification.adapter.out.client.application.UpcomingNextStepsGatewayAdapter;
import com.davidcreate.jobhub.notification.domain.model.UpcomingNextStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpcomingNextStepsGatewayAdapter Unit Tests")
class UpcomingNextStepsGatewayAdapterTest {

    @Mock
    AppInternalRestClient restClient;

    UpcomingNextStepsGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UpcomingNextStepsGatewayAdapter(restClient, "test-service-key");
    }

    // TC-140
    @Test
    @DisplayName("gateway_adapter_maps_response_items_to_domain_upcoming_next_steps")
    void mapsResponseItemsToDomainUpcomingNextSteps() {
        UUID userId1 = UUID.randomUUID();
        UUID applicationId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        UUID applicationId2 = UUID.randomUUID();
        OffsetDateTime reminderAt = OffsetDateTime.now();

        UpcomingNextStepItem withCompany = new UpcomingNextStepItem()
                .userId(userId1)
                .applicationId(applicationId1)
                .nextStepLabel("Interview with Product Manager")
                .nextStepDate(LocalDate.of(2026, 6, 17))
                .nextStepReminderAt(reminderAt)
                .companyName("Acme Corp")
                .status(ApplicationStatus.INTERVIEWING);

        UpcomingNextStepItem withoutCompany = new UpcomingNextStepItem()
                .userId(userId2)
                .applicationId(applicationId2)
                .nextStepLabel("Onsite interview")
                .nextStepDate(LocalDate.of(2026, 6, 18))
                .nextStepReminderAt(null)
                .companyName(null)
                .status(ApplicationStatus.INTERVIEWING);

        UpcomingNextStepsResponse response = new UpcomingNextStepsResponse()
                .items(List.of(withCompany, withoutCompany));

        when(restClient.getUpcomingNextSteps(eq(26), eq("test-service-key"))).thenReturn(response);

        List<UpcomingNextStep> result = adapter.fetch(26);

        assertThat(result).hasSize(2);

        UpcomingNextStep first = result.get(0);
        assertThat(first.getUserId()).isEqualTo(userId1);
        assertThat(first.getApplicationId()).isEqualTo(applicationId1);
        assertThat(first.getLabel()).isEqualTo("Interview with Product Manager");
        assertThat(first.getStepDate()).isEqualTo(LocalDate.of(2026, 6, 17));
        assertThat(first.getReminderAt()).isEqualTo(reminderAt);
        assertThat(first.getCompany()).isEqualTo("Acme Corp");
        assertThat(first.getStatus()).isEqualTo("interviewing");

        UpcomingNextStep second = result.get(1);
        assertThat(second.getUserId()).isEqualTo(userId2);
        assertThat(second.getApplicationId()).isEqualTo(applicationId2);
        assertThat(second.getCompany()).isNull();
        assertThat(second.getReminderAt()).isNull();

        verify(restClient).getUpcomingNextSteps(eq(26), eq("test-service-key"));
    }

    // TC-140b
    @Test
    @DisplayName("gateway_adapter_maps_empty_items_to_empty_list")
    void mapsEmptyItemsToEmptyList() {
        UpcomingNextStepsResponse response = new UpcomingNextStepsResponse().items(List.of());

        when(restClient.getUpcomingNextSteps(anyInt(), eq("test-service-key"))).thenReturn(response);

        List<UpcomingNextStep> result = adapter.fetch(26);

        assertThat(result).isEmpty();
    }
}
