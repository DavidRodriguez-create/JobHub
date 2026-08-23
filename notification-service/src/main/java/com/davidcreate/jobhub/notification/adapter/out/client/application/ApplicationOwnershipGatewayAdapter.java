package com.davidcreate.jobhub.notification.adapter.out.client.application;

import com.davidcreate.jobhub.notification.domain.port.out.ApplicationOwnershipGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.UUID;

@ApplicationScoped
public class ApplicationOwnershipGatewayAdapter implements ApplicationOwnershipGateway {

    private static final Logger LOG = Logger.getLogger(ApplicationOwnershipGatewayAdapter.class);

    private final AppInternalRestClient restClient;
    private final String serviceKey;

    public ApplicationOwnershipGatewayAdapter(@RestClient AppInternalRestClient restClient,
                                               @ConfigProperty(name = "notification.internal.service-key") String serviceKey) {
        this.restClient = restClient;
        this.serviceKey = serviceKey;
    }

    @Override
    public boolean isOwnedByUser(UUID applicationId, UUID userId) {
        try (Response response = restClient.headOwner(applicationId, userId, serviceKey)) {
            return response.getStatus() == 204;
        } catch (RuntimeException e) {
            LOG.errorf(e, "Application ownership check failed for applicationId=%s userId=%s", applicationId, userId);
            throw e;
        }
    }
}
