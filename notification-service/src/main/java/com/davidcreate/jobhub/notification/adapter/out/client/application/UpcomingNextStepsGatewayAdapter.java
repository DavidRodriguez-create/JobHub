package com.davidcreate.jobhub.notification.adapter.out.client.application;

import com.davidcreate.jobhub.application.contract.model.UpcomingNextStepItem;
import com.davidcreate.jobhub.application.contract.model.UpcomingNextStepsResponse;
import com.davidcreate.jobhub.notification.domain.model.UpcomingNextStep;
import com.davidcreate.jobhub.notification.domain.port.out.UpcomingNextStepsGateway;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
public class UpcomingNextStepsGatewayAdapter implements UpcomingNextStepsGateway {

    private final AppInternalRestClient restClient;
    private final String serviceKey;

    public UpcomingNextStepsGatewayAdapter(@RestClient AppInternalRestClient restClient,
                                            @ConfigProperty(name = "notification.internal.service-key") String serviceKey) {
        this.restClient = restClient;
        this.serviceKey = serviceKey;
    }

    @Override
    public List<UpcomingNextStep> fetch(int withinHours) {
        UpcomingNextStepsResponse response = restClient.getUpcomingNextSteps(withinHours, serviceKey);
        return response.getItems().stream()
                .map(this::toDomain)
                .toList();
    }

    private UpcomingNextStep toDomain(UpcomingNextStepItem item) {
        return UpcomingNextStep.builder()
                .userId(item.getUserId())
                .applicationId(item.getApplicationId())
                .label(item.getNextStepLabel())
                .stepDate(item.getNextStepDate())
                .reminderAt(item.getNextStepReminderAt())
                .company(item.getCompanyName())
                .status(item.getStatus() != null ? item.getStatus().toString() : null)
                .build();
    }
}
