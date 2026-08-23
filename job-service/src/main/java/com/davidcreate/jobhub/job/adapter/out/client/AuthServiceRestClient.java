package com.davidcreate.jobhub.job.adapter.out.client;

import com.davidcreate.jobhub.auth.contract.model.TwoFactorStatusResponse;
import com.davidcreate.jobhub.auth.contract.model.VerifyTwoFactorRequest;
import com.davidcreate.jobhub.auth.contract.model.VerifyTwoFactorResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.UUID;

/**
 * Service-to-service admin 2FA gate (ADR 0019). auth-service runs at root-path
 * {@code /auth}, so every path here bakes that in explicitly.
 */
@RegisterRestClient(configKey = "auth-service")
@Path("/auth/internal")
public interface AuthServiceRestClient {

    @GET
    @Path("/users/{userId}/two-factor")
    @Produces(MediaType.APPLICATION_JSON)
    TwoFactorStatusResponse getTwoFactorStatus(@PathParam("userId") UUID userId,
                                                @HeaderParam("X-Service-Key") String serviceKey);

    @POST
    @Path("/two-factor/verify")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    VerifyTwoFactorResponse verifyTwoFactor(VerifyTwoFactorRequest body,
                                             @HeaderParam("X-Service-Key") String serviceKey);
}
