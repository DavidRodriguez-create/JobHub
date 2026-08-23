package com.davidcreate.jobhub.auth.adapter.out.client.github;

import com.davidcreate.jobhub.github.contract.model.GithubEmailResponse;
import com.davidcreate.jobhub.github.contract.model.GithubUserResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "github-oauth-api")
public interface GithubApiClient {

    @GET
    @Path("/user")
    @Produces(MediaType.APPLICATION_JSON)
    GithubUserResponse user(@HeaderParam("Authorization") String bearerToken);

    @GET
    @Path("/user/emails")
    @Produces(MediaType.APPLICATION_JSON)
    List<GithubEmailResponse> emails(@HeaderParam("Authorization") String bearerToken);
}
