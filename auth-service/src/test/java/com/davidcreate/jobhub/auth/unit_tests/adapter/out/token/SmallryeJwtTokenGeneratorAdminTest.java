package com.davidcreate.jobhub.auth.unit_tests.adapter.out.token;

import com.davidcreate.jobhub.auth.adapter.out.token.SmallryeJwtTokenGenerator;
import com.davidcreate.jobhub.auth.domain.entity.User;
import io.smallrye.jwt.auth.principal.JWTAuthContextInfo;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A-U-01: admin group added for allowlist match
 * A-U-02: admin group omitted for non-allowlist email
 * A-U-03: admin group omitted when allowlist is empty
 * A-U-04: allowlist match is case-insensitive
 *
 * These tests construct SmallryeJwtTokenGenerator directly (no CDI),
 * set the allowlist via the package-private field approach (reflection) and
 * parse the resulting JWT to assert on the groups claim.
 *
 * Note: The test JWT keys are loaded from the test classpath
 * (META-INF/keys/publicKey.pem + privateKey.pem generated at build time).
 * We sign with the same key as the test profile, so parsing works.
 */
@DisplayName("SmallryeJwtTokenGenerator — Admin Allowlist Tests (A-U-01..04)")
class SmallryeJwtTokenGeneratorAdminTest {

    private SmallryeJwtTokenGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SmallryeJwtTokenGenerator();
        generator.lifespanSeconds = 86400L;
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private User userWithEmail(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .firstName("Test")
                .lastName("User")
                .passwordHash("hash")
                .emailVerified(true)
                .build();
    }

    private Set<String> groupsFromToken(String token) throws Exception {
        // Parse the JWT payload without full signature verification
        // (split on '.', base64-decode middle, read groups claim)
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        // Simple extraction — avoid pulling in full JWT library in unit test scope
        // groups claim is an array like "groups":["user","admin"]
        if (!payload.contains("\"groups\"")) {
            return Set.of();
        }
        String after = payload.substring(payload.indexOf("\"groups\"") + 9);
        after = after.substring(after.indexOf('[') + 1);
        after = after.substring(0, after.indexOf(']'));
        Set<String> groups = new java.util.HashSet<>();
        for (String raw : after.split(",")) {
            String v = raw.trim().replace("\"", "");
            if (!v.isEmpty()) groups.add(v);
        }
        return groups;
    }

    // ── A-U-01 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A-U-01: admin group present for allowlist email")
    void adminGroupAddedForAllowlistEmail() throws Exception {
        generator.adminEmailsConfig = "admin@example.com";

        String token = generator.generate(userWithEmail("admin@example.com"));

        Set<String> groups = groupsFromToken(token);
        assertThat(groups).contains("user", "admin");
    }

    // ── A-U-02 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A-U-02: admin group absent for non-allowlist email")
    void adminGroupAbsentForNonAllowlistEmail() throws Exception {
        generator.adminEmailsConfig = "admin@example.com";

        String token = generator.generate(userWithEmail("user@example.com"));

        Set<String> groups = groupsFromToken(token);
        assertThat(groups).contains("user");
        assertThat(groups).doesNotContain("admin");
    }

    // ── A-U-03 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A-U-03: admin group absent when allowlist is empty")
    void adminGroupAbsentWhenAllowlistEmpty() throws Exception {
        generator.adminEmailsConfig = "";

        String token = generator.generate(userWithEmail("anyone@example.com"));

        Set<String> groups = groupsFromToken(token);
        assertThat(groups).contains("user");
        assertThat(groups).doesNotContain("admin");
    }

    // ── A-U-04 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A-U-04: allowlist match is case-insensitive")
    void allowlistMatchIsCaseInsensitive() throws Exception {
        generator.adminEmailsConfig = "Admin@Example.COM";

        String token = generator.generate(userWithEmail("admin@example.com"));

        Set<String> groups = groupsFromToken(token);
        assertThat(groups).contains("admin");
    }
}
