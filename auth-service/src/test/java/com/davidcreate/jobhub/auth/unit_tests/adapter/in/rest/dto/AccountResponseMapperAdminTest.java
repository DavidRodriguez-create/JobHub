package com.davidcreate.jobhub.auth.unit_tests.adapter.in.rest.dto;

import com.davidcreate.jobhub.auth.adapter.in.rest.dto.AccountResponseMapper;
import com.davidcreate.jobhub.auth.contract.model.AccountResponse;
import com.davidcreate.jobhub.auth.domain.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A-U-05: mapper surfaces isAdmin=true and groups for admin user
 * A-U-06: mapper surfaces isAdmin=false and groups=["user"] for regular user
 */
@DisplayName("AccountResponseMapper — Admin fields (A-U-05..06)")
class AccountResponseMapperAdminTest {

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

    // ── A-U-05 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A-U-05: toAccount with isAdmin=true sets isAdmin=true and groups=[user,admin]")
    void toAccountSetsAdminFields() {
        User user = userWithEmail("admin@example.com");

        AccountResponse response = AccountResponseMapper.toAccount(user, true, List.of("user", "admin"));

        assertThat(response.getIsAdmin()).isTrue();
        assertThat(response.getGroups()).containsExactlyInAnyOrder("user", "admin");
    }

    // ── A-U-06 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A-U-06: toAccount with isAdmin=false sets isAdmin=false and groups=[user]")
    void toAccountSetsNonAdminFields() {
        User user = userWithEmail("regular@example.com");

        AccountResponse response = AccountResponseMapper.toAccount(user, false, List.of("user"));

        assertThat(response.getIsAdmin()).isFalse();
        assertThat(response.getGroups()).containsExactly("user");
        assertThat(response.getGroups()).doesNotContain("admin");
    }
}
