package com.davidcreate.jobhub.application.adapter.in.rest.filter;

import com.davidcreate.jobhub.application.contract.model.ErrorResponse;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Guards every {@code /internal/*} path with a pre-shared {@code X-Service-Key} header
 * (ADR 0008). This is service-to-service authentication, not user identity — it does not
 * interact with JWT/RolesAllowed at all. Requests to non-internal paths pass through untouched.
 */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION)
public class ServiceKeyFilter implements ContainerRequestFilter {

    private static final String HEADER = "X-Service-Key";
    private static final String INTERNAL_PREFIX = "internal/";

    private final String expectedKey;

    public ServiceKeyFilter(@ConfigProperty(name = "jobhub.internal.service-key") String expectedKey) {
        this.expectedKey = expectedKey;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        if (path == null || !path.startsWith(INTERNAL_PREFIX) && !path.startsWith("/" + INTERNAL_PREFIX)) {
            return;
        }

        String providedKey = requestContext.getHeaderString(HEADER);
        if (providedKey == null || !providedKey.equals(expectedKey)) {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse()
                            .error("Unauthorized")
                            .message("Missing or invalid X-Service-Key"))
                    .build());
        }
    }
}
