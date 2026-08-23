package com.davidcreate.jobhub.auth.adapter.out.client.google;

import com.davidcreate.jobhub.google.contract.model.GoogleUserInfoResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "google-oauth-userinfo")
public interface GoogleUserInfoClient {

    @GET
    @Path("/v1/userinfo")
    @Produces(MediaType.APPLICATION_JSON)
    GoogleUserInfoResponse userInfo(@HeaderParam("Authorization") String bearerToken);
}
