package com.davidcreate.jobhub.application.adapter.out.client;

import com.davidcreate.jobhub.application.application.port.out.VerificationGateway;
import com.davidcreate.jobhub.application.domain.exception.InvalidVerificationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.UUID;

@ApplicationScoped
public class VerificationGatewayAdapter implements VerificationGateway {

    private static final Logger LOG = Logger.getLogger(VerificationGatewayAdapter.class);
    private static final String ACTION_DELETE_ALL_APPLICATIONS = "delete-all-applications";

    @Inject
    @RestClient
    AuthServiceRestClient client;

    @Override
    public void consumeDeleteAllApplications(String bearerToken, UUID verificationId, String code) {
        try {
            client.consume(bearerToken, new AuthServiceRestClient.ConsumeVerificationBody(
                    verificationId, code, ACTION_DELETE_ALL_APPLICATIONS));
        } catch (BadRequestException e) {
            throw new InvalidVerificationException("verification code is invalid or expired");
        } catch (WebApplicationException e) {
            LOG.errorf("auth-service returned %d while consuming verification %s",
                    e.getResponse().getStatus(), verificationId);
            throw e;
        }
    }
}
