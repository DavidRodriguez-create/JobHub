package com.davidcreate.jobhub.auth.domain.valueobject;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure domain rule for ADR 0003 admin identity: an account is an administrator
 * if and only if its email (case-insensitive) appears in a deployment-configured,
 * comma-separated allowlist (auth.admin.emails / AUTH_ADMIN_EMAILS).
 *
 * An empty or blank allowlist means no admins exist. The same rule drives both
 * the JWT "groups" claim and AccountResponse.isAdmin/.groups, so they are always
 * consistent.
 */
public final class AdminAllowlist {

    public static final String USER_GROUP = "user";
    public static final String ADMIN_GROUP = "admin";

    private AdminAllowlist() {
    }

    public static boolean isAdmin(String allowlistConfig, String email) {
        if (allowlistConfig == null || allowlistConfig.isBlank() || email == null) {
            return false;
        }
        String normalizedEmail = email.trim().toLowerCase();
        return Arrays.stream(allowlistConfig.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(String::toLowerCase)
                .anyMatch(entry -> entry.equals(normalizedEmail));
    }

    public static List<String> groupsFor(String allowlistConfig, String email) {
        Set<String> groups = new LinkedHashSet<>();
        groups.add(USER_GROUP);
        if (isAdmin(allowlistConfig, email)) {
            groups.add(ADMIN_GROUP);
        }
        return List.copyOf(groups);
    }
}
