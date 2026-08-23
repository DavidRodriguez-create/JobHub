package com.davidcreate.jobhub.job.adapter.in.rest.exception;

import com.davidcreate.jobhub.job.domain.exception.TriggeringDisabledException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/**
 * Maps {@link TriggeringDisabledException} to a 403 with a body distinct from the standard
 * {@code @RolesAllowed} policy denial (ADR 0003 §6) so the UI can tell "you're not an admin"
 * apart from "triggering is disabled by deployment config".
 */
@Provider
public class TriggeringDisabledExceptionMapper implements ExceptionMapper<TriggeringDisabledException> {

    @Override
    public Response toResponse(TriggeringDisabledException exception) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(Map.of(
                        "error", "Triggering Disabled",
                        "message", exception.getMessage()))
                .build();
    }
}
