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
import java.util.Iterator;
import java.util.Set;

/**
 * Rejects {@code PUT /notifications/preferences} bodies that contain at least one JSON
 * property but none of the four recognised preference fields (BR-6 / TC-16a).
 *
 * <p>The generated {@code UpdateNotificationPreferencesRequest} model has no
 * {@code additionalProperties: false} in the OpenAPI spec, so Jackson silently ignores
 * unknown properties by default — a body like {@code {"smsAlerts": true}} would otherwise
 * deserialize identically to {@code {}} and be accepted as a (no-op) partial update.
 *
 * <p>An empty object {@code {}} remains valid per BR-3 (a true no-op partial update).
 */
@Provider
@Priority(Priorities.ENTITY_CODER)
public class UnrecognisedPreferencesFilter implements ContainerRequestFilter {

    private static final Set<String> RECOGNISED_FIELDS = Set.of(
            "weeklyDigestEmail", "inAppNotificationsEnabled", "interviewReminders",
            "interviewReminderEmail", "ghostedAlert");

    private final ObjectMapper objectMapper;

    @Inject
    public UnrecognisedPreferencesFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!HttpMethod.PUT.equals(requestContext.getMethod())) {
            return;
        }
        if (!requestContext.getUriInfo().getPath().endsWith("notifications/preferences")) {
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
            // Malformed JSON: let the normal Jackson body reader produce the error.
            return;
        }

        if (root == null || !root.isObject() || root.isEmpty()) {
            return;
        }

        Iterator<String> fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            if (RECOGNISED_FIELDS.contains(fieldNames.next())) {
                return;
            }
        }

        requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(new GenericExceptionMapper.ErrorBody(
                        "Bad Request",
                        "Request body must contain at least one of: weeklyDigestEmail, "
                                + "inAppNotificationsEnabled, interviewReminders, interviewReminderEmail, ghostedAlert"))
                .build());
    }
}
