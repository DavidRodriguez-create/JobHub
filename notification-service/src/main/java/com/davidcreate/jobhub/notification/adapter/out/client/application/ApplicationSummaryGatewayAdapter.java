package com.davidcreate.jobhub.notification.adapter.out.client.application;

import com.davidcreate.jobhub.application.contract.model.ApplicationSummaryListResponse;
import com.davidcreate.jobhub.application.contract.model.ApplicationSummaryResponse;
import com.davidcreate.jobhub.notification.domain.model.ApplicationSummary;
import com.davidcreate.jobhub.notification.domain.port.out.ApplicationSummaryGateway;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class ApplicationSummaryGatewayAdapter implements ApplicationSummaryGateway {

    private static final Logger LOG = Logger.getLogger(ApplicationSummaryGatewayAdapter.class);

    private final AppInternalRestClient restClient;
    private final String serviceKey;

    public ApplicationSummaryGatewayAdapter(@RestClient AppInternalRestClient restClient,
                                             @ConfigProperty(name = "notification.internal.service-key") String serviceKey) {
        this.restClient = restClient;
        this.serviceKey = serviceKey;
    }

    @Override
    public Map<UUID, ApplicationSummary> resolve(Set<UUID> applicationIds) {
        if (applicationIds.isEmpty()) {
            return Map.of();
        }

        String ids = applicationIds.stream().map(UUID::toString).collect(Collectors.joining(","));

        ApplicationSummaryListResponse response;
        try {
            response = restClient.getApplicationSummaries(ids, serviceKey);
        } catch (RuntimeException e) {
            LOG.warnf(e, "application-service summaries call failed for %d application id(s)",
                    applicationIds.size());
            throw e;
        }

        return response.getItems().stream()
                .collect(Collectors.toMap(
                        ApplicationSummaryResponse::getApplicationId,
                        this::toDomain));
    }

    private ApplicationSummary toDomain(ApplicationSummaryResponse r) {
        return ApplicationSummary.builder()
                .applicationId(r.getApplicationId())
                .company(r.getCompany())
                .jobTitle(r.getJobTitle())
                .companyLogoUrl(r.getCompanyLogoUrl())
                .build();
    }
}
