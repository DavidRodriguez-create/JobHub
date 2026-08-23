package com.davidcreate.jobhub.notification.adapter.out.client.auth;

import com.davidcreate.jobhub.auth.contract.model.UsersWithoutTwoFactorResponse;
import com.davidcreate.jobhub.notification.domain.port.out.TwoFactorStatusGateway;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TwoFactorStatusGatewayAdapter implements TwoFactorStatusGateway {

    private final AuthInternalRestClient restClient;
    private final String serviceKey;

    public TwoFactorStatusGatewayAdapter(@RestClient AuthInternalRestClient restClient,
                                          @ConfigProperty(name = "notification.internal.service-key") String serviceKey) {
        this.restClient = restClient;
        this.serviceKey = serviceKey;
    }

    @Override
    public List<UUID> fetchUsersWithoutTwoFactor() {
        UsersWithoutTwoFactorResponse response = restClient.getUsersWithoutTwoFactor(serviceKey);
        return response.getUserIds();
    }
}
