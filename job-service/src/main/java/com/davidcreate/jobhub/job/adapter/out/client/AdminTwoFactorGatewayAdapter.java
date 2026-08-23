package com.davidcreate.jobhub.job.adapter.out.client;

import com.davidcreate.jobhub.auth.contract.model.VerifyTwoFactorRequest;
import com.davidcreate.jobhub.auth.contract.model.VerifyTwoFactorResponse;
import com.davidcreate.jobhub.job.domain.exception.VerificationRequiredException;
import com.davidcreate.jobhub.job.domain.exception.VerificationThrottledException;
import com.davidcreate.jobhub.job.domain.port.out.AdminTwoFactorGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.UUID;

@ApplicationScoped
public class AdminTwoFactorGatewayAdapter implements AdminTwoFactorGateway {

    private static final Logger LOG = Logger.getLogger(AdminTwoFactorGatewayAdapter.class);

    private final AuthServiceRestClient client;
    private final String serviceKey;

    public AdminTwoFactorGatewayAdapter(@RestClient AuthServiceRestClient client,
                                         @ConfigProperty(name = "jobhub.internal.service-key") String serviceKey) {
        this.client = client;
        this.serviceKey = serviceKey;
    }

    @Override
    public boolean isEnabled(UUID userId) {
        try {
            return Boolean.TRUE.equals(client.getTwoFactorStatus(userId, serviceKey).getTwoFactorEnabled());
        } catch (WebApplicationException e) {
            LOG.errorf("auth-service returned %d while reading 2FA status for admin %s",
                    e.getResponse().getStatus(), userId);
            throw new RuntimeException("auth-service returned an unexpected error resolving 2FA status", e);
        }
    }

    @Override
    public void verify(UUID userId, String code) {
        try {
            VerifyTwoFactorResponse response = client.verifyTwoFactor(
                    new VerifyTwoFactorRequest().userId(userId).code(code), serviceKey);
            // Both outcomes (verified / not_enrolled) authorize the caller to proceed;
            // job-service does not distinguish them (ADR 0019).
            LOG.debugf("admin %s 2FA verify outcome: %s", userId, response.getOutcome());
        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            if (status == 422) {
                throw new VerificationRequiredException(
                        "two-factor code is missing, invalid, expired, or already used");
            }
            if (status == Response.Status.TOO_MANY_REQUESTS.getStatusCode()) {
                throw new VerificationThrottledException("too many verification attempts, try again later");
            }
            LOG.errorf("auth-service returned %d while verifying 2FA for admin %s", status, userId);
            throw new RuntimeException("auth-service returned an unexpected error verifying 2FA", e);
        }
    }
}
