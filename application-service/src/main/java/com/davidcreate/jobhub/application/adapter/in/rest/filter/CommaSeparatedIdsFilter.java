package com.davidcreate.jobhub.application.adapter.in.rest.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.ext.Provider;

import java.net.URI;
import java.util.List;

/**
 * The frozen {@code GET /internal/applications/summaries?ids=...} contract uses OpenAPI
 * {@code style: form, explode: false} (a single comma-joined query value), but RESTEasy
 * Reactive's default {@code @QueryParam List<T>} binding only splits on repeated
 * {@code ids=a&ids=b} parameters, not commas. Rewriting the single comma-joined {@code ids}
 * value into repeated query parameters here (before routing/param-binding) lets the generated
 * {@code InternalApi#getApplicationSummaries(List<UUID>)} signature stay untouched, matching
 * the contract as designed without forking the generated interface.
 */
@Provider
@PreMatching
@Priority(Priorities.HEADER_DECORATOR)
public class CommaSeparatedIdsFilter implements ContainerRequestFilter {

    private static final String PATH = "internal/applications/summaries";
    private static final String PARAM = "ids";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        if (path == null || !(path.equals(PATH) || path.equals("/" + PATH))) {
            return;
        }
        List<String> values = requestContext.getUriInfo().getQueryParameters().get(PARAM);
        if (values == null || values.size() != 1 || !values.get(0).contains(",")) {
            return;
        }
        String rawValue = values.get(0);
        UriBuilder builder = UriBuilder.fromUri(requestContext.getUriInfo().getRequestUri())
                .replaceQueryParam(PARAM);
        for (String token : rawValue.split(",", -1)) {
            builder.queryParam(PARAM, token);
        }
        URI rewritten = builder.build();
        requestContext.setRequestUri(rewritten);
    }
}
