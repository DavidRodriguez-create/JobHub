package com.davidcreate.jobhub.notification.adapter.in.rest.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Rejects {@code POST}/{@code PUT} bodies on the custom-reminder endpoints that explicitly
 * supply {@code "channels": []}. The generated request models default the {@code channels}
 * field to an empty list, so an omitted field and an explicitly empty array are otherwise
 * indistinguishable by the time the JAX-RS layer sees the deserialized bean; this filter reads
 * the raw JSON to detect the explicit-empty case before that information is lost.
 */
@Provider
@Priority(Priorities.ENTITY_CODER)
public class EmptyChannelsRequestFilter implements ContainerRequestFilter {

    private final ObjectMapper objectMapper;

    @Inject
    public EmptyChannelsRequestFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String method = requestContext.getMethod();
        if (!HttpMethod.POST.equals(method) && !HttpMethod.PUT.equals(method)) {
            return;
        }
        if (!requestContext.getUriInfo().getPath().contains("custom-reminders")) {
            return;
        }

        byte[] body = requestContext.getEntityStream().readAllBytes();
        requestContext.setEntityStream(new ByteArrayInputStream(body));

        if (body.length == 0) {
            return;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException e) {
            return;
        }

        if (root == null || !root.isObject() || !root.has("channels")) {
            return;
        }

        JsonNode channels = root.get("channels");
        if (channels.isArray() && channels.isEmpty()) {
            requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new GenericExceptionMapper.ErrorBody(
                            "Invalid Channels", "channels must contain at least one entry"))
                    .build());
        }
    }
}
