package com.davidcreate.jobhub.auth.unit_tests.domain.service;

import com.davidcreate.jobhub.auth.domain.service.ProviderDisplayName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure Layer 1 domain rule (ADR 0028, Decision 3), highest-priority unit surface
 * per the PDA's own flag. Covers TC-506-A11..A21.
 */
@DisplayName("ProviderDisplayName Unit Tests")
class ProviderDisplayNameTest {

    // TC-506-A11: DN-1 (regression). Provider's own fields win outright.
    @Test
    @DisplayName("TC-506-A11: both first/last name present -> derived pair is exactly those two values")
    void bothNamesPresentPassThroughUnchanged() {
        ProviderDisplayName.Name name = ProviderDisplayName.derive(
                "Alex", "Morales", "Someone Else Entirely", "someone-else", "someone.else@example.com");

        assertThat(name.firstName()).isEqualTo("Alex");
        assertThat(name.lastName()).isEqualTo("Morales");
    }

    // TC-506-A12: DN-2 (the reported production bug).
    @Test
    @DisplayName("TC-506-A12: given name only, fullName is the same single token -> lastName is \"\"")
    void givenNameOnlySingleTokenFullName() {
        ProviderDisplayName.Name name = ProviderDisplayName.derive("Alex", null, "Alex", null, null);

        assertThat(name.firstName()).isEqualTo("Alex");
        assertThat(name.lastName()).isEqualTo("");
    }

    // TC-506-A13: DN-3.
    @Test
    @DisplayName("TC-506-A13: given name only, fuller fullName -> lastName derived from fullName's remainder")
    void givenNameOnlyFullerFullName() {
        ProviderDisplayName.Name name = ProviderDisplayName.derive("Alex", null, "Alex Morales", null, null);

        assertThat(name.firstName()).isEqualTo("Alex");
        assertThat(name.lastName()).isEqualTo("Morales");
    }

    // TC-506-A14: DN-4 (defensive edge, explicitly kept in scope).
    @Test
    @DisplayName("TC-506-A14: no usable name signal at all -> falls to the email's local part")
    void noNameSignalFallsBackToEmailLocalPart() {
        ProviderDisplayName.Name name = ProviderDisplayName.derive(null, null, null, null, "j.smith@example.com");

        assertThat(name.firstName()).isEqualTo("j.smith");
        assertThat(name.lastName()).isEqualTo("");
    }

    // TC-506-A15: DN-5.
    @Test
    @DisplayName("TC-506-A15: no provider first/last fields, two-word fullName -> split on first token")
    void twoWordFullNameSplitsOnFirstToken() {
        ProviderDisplayName.Name name = ProviderDisplayName.derive(null, null, "Ada Lovelace", null, null);

        assertThat(name.firstName()).isEqualTo("Ada");
        assertThat(name.lastName()).isEqualTo("Lovelace");
    }

    // TC-506-A16: DN-6.
    @Test
    @DisplayName("TC-506-A16: mononym fullName -> lastName is \"\", nothing fabricated")
    void mononymFullNameYieldsEmptyLastName() {
        ProviderDisplayName.Name name = ProviderDisplayName.derive(null, null, "Madonna", null, null);

        assertThat(name.firstName()).isEqualTo("Madonna");
        assertThat(name.lastName()).isEqualTo("");
    }

    // TC-506-A17: DN-7 (regression, generalizes #459's GitHub-only fallback).
    @Test
    @DisplayName("TC-506-A17: no fullName, username present -> firstName falls back to username")
    void noFullNameFallsBackToUsername() {
        ProviderDisplayName.Name name = ProviderDisplayName.derive(null, null, null, "octocat", null);

        assertThat(name.firstName()).isEqualTo("octocat");
        assertThat(name.lastName()).isEqualTo("");
    }

    // TC-506-A18: DN-8.
    @Test
    @DisplayName("TC-506-A18: irregular whitespace is trimmed and internal spaces collapsed")
    void irregularWhitespaceIsNormalized() {
        ProviderDisplayName.Name name = ProviderDisplayName.derive(null, null, "  Grace   Hopper  ", null, null);

        assertThat(name.firstName()).isEqualTo("Grace");
        assertThat(name.lastName()).isEqualTo("Hopper");
    }

    // TC-506-A19: DN-9.
    @Test
    @DisplayName("TC-506-A19: an over-length name is truncated to 100 characters, derivation still completes")
    void overLengthNameIsTruncated() {
        String longToken = "A".repeat(150);

        ProviderDisplayName.Name name = ProviderDisplayName.derive(longToken, longToken, null, null, null);

        assertThat(name.firstName()).hasSize(100);
        assertThat(name.lastName()).hasSize(100);
    }

    // TC-506-A20: never-null guarantee (defensive, no scenario ID of its own).
    @Test
    @DisplayName("TC-506-A20: absolutely no signal at all -> \"User\"/\"\", never null, never throws")
    void noSignalAtAllFallsBackToLiteralUser() {
        ProviderDisplayName.Name name = ProviderDisplayName.derive(null, null, null, null, null);

        assertThat(name.firstName()).isEqualTo("User");
        assertThat(name.lastName()).isEqualTo("");
    }

    // TC-506-A21: cascade-order proof - username wins over email-local-part.
    @Test
    @DisplayName("TC-506-A21: username present alongside a usable email -> username wins the fallback")
    void usernameWinsOverEmailLocalPart() {
        ProviderDisplayName.Name name = ProviderDisplayName.derive(
                null, null, null, "octocat", "someone@example.com");

        assertThat(name.firstName()).isEqualTo("octocat");
    }
}
