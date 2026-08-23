package com.davidcreate.jobhub.auth.application.port.in;

import com.davidcreate.jobhub.auth.domain.valueobject.OAuthProvider;

/**
 * One provider and whether this deployment holds usable credentials for it
 * (ADR 0028, Decision 2). {@code available} is configuration only, never a
 * health probe. Distinct from the generated contract model of the same simple
 * name ({@code com.davidcreate.jobhub.auth.contract.model.OAuthProviderAvailability}):
 * this is the Layer 2 port/use-case shape, mapped to the contract model only at
 * the REST boundary in {@code OAuthResource}.
 */
public record OAuthProviderAvailability(OAuthProvider provider, boolean available) {
}
