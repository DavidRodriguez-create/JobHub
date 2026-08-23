package com.davidcreate.jobhub.auth.adapter.in.rest.filter;

import com.davidcreate.jobhub.auth.domain.exception.InvalidServiceKeyException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Protects {@code /internal/*} endpoints (ADR 0008) with a pre-shared API key exchanged
 * via the {@code X-Service-Key} header. Requests to any other path pass through unchanged
 * — user-facing endpoints keep their existing JWT-based security.
 */
@Provider
public class ServiceKeyFilter implements ContainerRequestFilter {

    private static final String SERVICE_KEY_HEADER = "X-Service-Key";
    private static final String INTERNAL_PATH_PREFIX = "internal";

    private final String expectedServiceKey;

    public ServiceKeyFilter(@ConfigProperty(name = "jobhub.internal.service-key") String expectedServiceKey) {
        this.expectedServiceKey = expectedServiceKey;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        if (!isInternalPath(path)) {
            return;
        }

        String providedKey = requestContext.getHeaderString(SERVICE_KEY_HEADER);
        if (providedKey == null || !providedKey.equals(expectedServiceKey)) {
            throw new InvalidServiceKeyException();
        }
    }

    private boolean isInternalPath(String path) {
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        return normalized.equals(INTERNAL_PATH_PREFIX) || normalized.startsWith(INTERNAL_PATH_PREFIX + "/");
    }
}
