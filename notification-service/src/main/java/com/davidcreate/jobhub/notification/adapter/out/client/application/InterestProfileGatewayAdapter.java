package com.davidcreate.jobhub.notification.adapter.out.client.application;

import com.davidcreate.jobhub.application.contract.model.InterestProfileResponse;
import com.davidcreate.jobhub.notification.domain.model.InterestProfile;
import com.davidcreate.jobhub.notification.domain.port.out.InterestProfileGateway;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.UUID;

@ApplicationScoped
public class InterestProfileGatewayAdapter implements InterestProfileGateway {

    private final AppInternalRestClient restClient;
    private final String serviceKey;

    public InterestProfileGatewayAdapter(@RestClient AppInternalRestClient restClient,
                                          @ConfigProperty(name = "notification.internal.service-key") String serviceKey) {
        this.restClient = restClient;
        this.serviceKey = serviceKey;
    }

    @Override
    public InterestProfile fetch(UUID userId) {
        InterestProfileResponse response = restClient.getUserInterestProfile(userId, serviceKey);
        return InterestProfile.builder()
                .userId(response.getUserId())
                .locations(response.getLocations())
                .companies(response.getCompanies())
                .keywords(response.getKeywords())
                .build();
    }
}
