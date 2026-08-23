package com.davidcreate.jobhub.auth.domain.service;

/**
 * Pure Layer 1 domain rule (ADR 0028, Decision 3, DN-BR1). Turns the raw signals
 * an OAuth provider adapter reports ({@link com.davidcreate.jobhub.auth.domain.valueobject.ExternalIdentity}'s
 * {@code firstName}/{@code lastName}/{@code fullName}/{@code username}, plus the
 * account's normalized email) into a never-null display name pair, so a missing
 * {@code family_name} (Google) or a name-only/login-only GitHub profile can never
 * 500 the just-in-time provisioning write. Zero framework annotations, zero I/O:
 * a plain static function, called only from the JIT branch of
 * {@code CompleteOAuthLoginService.resolveUser} (DN-BR2, never on the existing-link
 * or auto-link paths).
 */
public final class ProviderDisplayName {

    private static final int MAX_LENGTH = 100;
    private static final String DEFAULT_FIRST_NAME = "User";

    private ProviderDisplayName() {
    }

    public record Name(String firstName, String lastName) {
    }

    /**
     * @param firstName the provider's own first-name field (Google's {@code given_name}; null for GitHub)
     * @param lastName  the provider's own last-name field (Google's {@code family_name}; null for GitHub)
     * @param fullName  the provider's full display name (Google's/GitHub's {@code name})
     * @param username  the provider's handle (GitHub's {@code login}; null for Google)
     * @param email     the account's normalized email, used only for its local part
     */
    public static Name derive(String firstName, String lastName, String fullName, String username, String email) {
        String firstToken = firstToken(fullName);
        String remainder = remainder(fullName);
        String emailLocalPart = localPart(email);

        String resolvedFirst = firstNonBlank(firstName, firstToken, username, emailLocalPart, DEFAULT_FIRST_NAME);
        String resolvedLast = firstNonBlank(lastName, remainder, "");

        return new Name(normalize(resolvedFirst), normalize(resolvedLast));
    }

    private static String firstToken(String fullName) {
        String normalized = collapseWhitespace(fullName);
        if (normalized.isEmpty()) {
            return "";
        }
        int spaceIndex = normalized.indexOf(' ');
        return spaceIndex < 0 ? normalized : normalized.substring(0, spaceIndex);
    }

    private static String remainder(String fullName) {
        String normalized = collapseWhitespace(fullName);
        int spaceIndex = normalized.indexOf(' ');
        return spaceIndex < 0 ? "" : normalized.substring(spaceIndex + 1);
    }

    private static String localPart(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        return at < 0 ? email : email.substring(0, at);
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    private static String collapseWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String normalize(String value) {
        String collapsed = collapseWhitespace(value);
        return collapsed.length() > MAX_LENGTH ? collapsed.substring(0, MAX_LENGTH) : collapsed;
    }
}
