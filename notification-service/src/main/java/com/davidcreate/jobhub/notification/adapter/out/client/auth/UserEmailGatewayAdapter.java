package com.davidcreate.jobhub.notification.adapter.out.client.auth;

import com.davidcreate.jobhub.auth.contract.model.UserEmailBatchResponse;
import com.davidcreate.jobhub.auth.contract.model.UserEmailEntry;
import com.davidcreate.jobhub.notification.domain.model.UserEmail;
import com.davidcreate.jobhub.notification.domain.port.out.UserEmailGateway;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class UserEmailGatewayAdapter implements UserEmailGateway {

    private final AuthInternalRestClient restClient;
    private final String serviceKey;

    public UserEmailGatewayAdapter(@RestClient AuthInternalRestClient restClient,
                                    @ConfigProperty(name = "notification.internal.service-key") String serviceKey) {
        this.restClient = restClient;
        this.serviceKey = serviceKey;
    }

    @Override
    public Map<UUID, String> fetchEmails(Set<UUID> userIds) {
        UserEmailBatchResponse response = restClient.getUserEmails(List.copyOf(userIds), serviceKey);

        List<UserEmail> userEmails = response.getEmails().stream()
                .map(this::toDomain)
                .toList();

        Map<UUID, String> result = new HashMap<>();
        for (UserEmail userEmail : userEmails) {
            result.put(userEmail.getUserId(), userEmail.getEmail());
        }
        return result;
    }

    private UserEmail toDomain(UserEmailEntry entry) {
        return UserEmail.builder()
                .userId(entry.getUserId())
                .email(entry.getEmail())
                .build();
    }
}
