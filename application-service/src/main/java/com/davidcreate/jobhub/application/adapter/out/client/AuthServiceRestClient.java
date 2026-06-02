package com.davidcreate.jobhub.application.adapter.out.client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.UUID;

@RegisterRestClient(configKey = "auth-service")
@Path("/auth/account/verifications/consume")
@Consumes(MediaType.APPLICATION_JSON)
public interface AuthServiceRestClient {

    @POST
    void consume(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization, ConsumeVerificationBody body);

    record ConsumeVerificationBody(UUID verificationId, String code, String action) {
    }
}
