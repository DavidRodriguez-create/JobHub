package com.davidcreate.jobhub.auth.application.port.in;

import java.util.List;

/**
 * {@code GET /oauth/providers} (ADR 0028, Decision 2): reports every provider
 * auth-service knows about, in stable declaration order (google, then github),
 * each with whether this deployment holds usable credentials for it. Gates the
 * pre-login screen, so it is deliberately unauthenticated at the REST boundary.
 */
public interface ListOAuthProvidersUseCase {

    List<OAuthProviderAvailability> list();
}
