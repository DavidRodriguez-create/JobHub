package com.davidcreate.jobhub.auth.adapter.out.client.github;

import com.davidcreate.jobhub.github.contract.model.GithubTokenResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "github-oauth-token")
public interface GithubTokenClient {

    @POST
    @Path("/login/oauth/access_token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    GithubTokenResponse exchange(@HeaderParam("Accept") String accept,
                                 @FormParam("client_id") String clientId,
                                 @FormParam("client_secret") String clientSecret,
                                 @FormParam("code") String code,
                                 @FormParam("redirect_uri") String redirectUri);
}
