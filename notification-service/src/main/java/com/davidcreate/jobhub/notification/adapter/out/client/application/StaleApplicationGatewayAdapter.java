package com.davidcreate.jobhub.notification.adapter.out.client.application;

import com.davidcreate.jobhub.application.contract.model.StaleApplicationListResponse;
import com.davidcreate.jobhub.application.contract.model.StaleApplicationResponse;
import com.davidcreate.jobhub.application.contract.model.UpdateApplicationStatusRequest;
import com.davidcreate.jobhub.notification.domain.exception.ApplicationAlreadyGhostedException;
import com.davidcreate.jobhub.notification.domain.model.StaleApplication;
import com.davidcreate.jobhub.notification.domain.port.out.StaleApplicationGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class StaleApplicationGatewayAdapter implements StaleApplicationGateway {

    private static final Logger LOG = Logger.getLogger(StaleApplicationGatewayAdapter.class);

    private final StaleApplicationRestClient restClient;
    private final String serviceKey;

    public StaleApplicationGatewayAdapter(@RestClient StaleApplicationRestClient restClient,
                                           @ConfigProperty(name = "notification.internal.service-key") String serviceKey) {
        this.restClient = restClient;
        this.serviceKey = serviceKey;
    }

    @Override
    public List<StaleApplication> listStaleApplications(int days) {
        StaleApplicationListResponse response = restClient.listStaleApplications(days, serviceKey);
        return response.getItems().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void updateApplicationStatusToGhosted(UUID applicationId) {
        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(com.davidcreate.jobhub.application.contract.model.ApplicationStatus.GHOSTED);
        try {
            restClient.updateApplicationStatus(applicationId, request, serviceKey);
        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            if (status == 409) {
                throw new ApplicationAlreadyGhostedException(applicationId);
            }
            LOG.errorf(e, "Ghosted-alert: application-service returned %d for application %s",
                    status, applicationId);
            throw new RuntimeException("application-service returned " + status + " for application " + applicationId, e);
        }
    }

    private StaleApplication toDomain(StaleApplicationResponse r) {
        return StaleApplication.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .jobTitle(r.getJobTitle())
                .company(r.getCompany())
                .daysSinceLastActivity(r.getDaysSinceLastActivity())
                .build();
    }
}
