package com.davidcreate.jobhub.auth.logging;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * One structured line per resource call: which handler ran, the (masked) request
 * and response payloads, status, and latency. Complements the HTTP access log,
 * which only carries the request line. Personal data in the payloads is redacted
 * downstream by {@link MaskingLogFilter}, so this never has to mask anything itself.
 *
 * <p>Only JSON bodies are logged, capped at {@code request-log.body-limit} chars.
 */
@Provider
public class RequestLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger("http.in");
    private static final String START_TIME = "jobhub.req.start-nanos";

    @Context
    ResourceInfo resourceInfo;

    @ConfigProperty(name = "jobhub.http.request-log.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "jobhub.http.request-log.body-limit", defaultValue = "2000")
    int bodyLimit;

    @ConfigProperty(name = "jobhub.http.slow-request.enabled", defaultValue = "true")
    boolean slowRequestEnabled;

    @ConfigProperty(name = "jobhub.http.slow-request.threshold-ms", defaultValue = "1000")
    long slowRequestThresholdMs;

    @Override
    public void filter(ContainerRequestContext req) throws IOException {
        req.setProperty(START_TIME, System.nanoTime());
        if (!enabled) {
            return;
        }
        LOG.infof("-> %s /%s%s%s",
                req.getMethod(), req.getUriInfo().getPath(), handler(), readRequestBody(req));
    }

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) {
        long elapsed = elapsedMs(req);
        if (elapsed >= 0 && slowRequestEnabled && elapsed >= slowRequestThresholdMs) {
            LOG.warnf("SLOW %s /%s %d took %dms (threshold %dms)",
                    req.getMethod(), req.getUriInfo().getPath(), res.getStatus(), elapsed, slowRequestThresholdMs);
        }
        if (!enabled) {
            return;
        }
        LOG.infof("<- %s /%s %d in %dms%s",
                req.getMethod(), req.getUriInfo().getPath(), res.getStatus(), elapsed,
                responseBody(res));
    }

    private String handler() {
        if (resourceInfo == null || resourceInfo.getResourceClass() == null) {
            return "";
        }
        return " (" + resourceInfo.getResourceClass().getSimpleName()
                + "." + resourceInfo.getResourceMethod().getName() + ")";
    }

    private String readRequestBody(ContainerRequestContext req) throws IOException {
        if (!req.hasEntity() || !isJson(req.getMediaType())) {
            return "";
        }
        byte[] bytes = req.getEntityStream().readAllBytes();
        req.setEntityStream(new ByteArrayInputStream(bytes)); // reset so the resource can still read it
        return " body=" + truncate(new String(bytes, StandardCharsets.UTF_8));
    }

    private String responseBody(ContainerResponseContext res) {
        if (!res.hasEntity() || !isJson(res.getMediaType())) {
            return "";
        }
        return " body=" + truncate(String.valueOf(res.getEntity()));
    }

    private long elapsedMs(ContainerRequestContext req) {
        return req.getProperty(START_TIME) instanceof Long start
                ? (System.nanoTime() - start) / 1_000_000
                : -1;
    }

    private boolean isJson(MediaType mt) {
        return mt != null && mt.isCompatible(MediaType.APPLICATION_JSON_TYPE);
    }

    private String truncate(String s) {
        String collapsed = s.replaceAll("\\s+", " ").trim();
        if (collapsed.length() > bodyLimit) {
            return collapsed.substring(0, bodyLimit) + "…(" + collapsed.length() + " chars)";
        }
        return collapsed;
    }
}
