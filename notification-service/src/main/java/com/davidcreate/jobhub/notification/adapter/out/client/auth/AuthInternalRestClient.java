package com.davidcreate.jobhub.notification.adapter.out.client.auth;

import com.davidcreate.jobhub.auth.contract.model.UserEmailBatchResponse;
import com.davidcreate.jobhub.auth.contract.model.UsersWithoutTwoFactorResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.UUID;

@RegisterRestClient(configKey = "auth-internal")
@Path("/auth/internal/users")
public interface AuthInternalRestClient {

    @GET
    @Path("/emails")
    @Produces(MediaType.APPLICATION_JSON)
    UserEmailBatchResponse getUserEmails(@QueryParam("userIds") List<UUID> userIds,
                                          @HeaderParam("X-Service-Key") String serviceKey);

    @GET
    @Path("/without-2fa")
    @Produces(MediaType.APPLICATION_JSON)
    UsersWithoutTwoFactorResponse getUsersWithoutTwoFactor(@HeaderParam("X-Service-Key") String serviceKey);
}
