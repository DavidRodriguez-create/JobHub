package com.davidcreate.jobhub.auth.unit_tests.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.UserEntity;
import com.davidcreate.jobhub.auth.adapter.out.persistence.mapper.UserMapper;
import com.davidcreate.jobhub.auth.domain.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserMapper Unit Tests")
class UserMapperTest {

    UserMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new UserMapper();
    }

    @Test
    @DisplayName("toDomain copies all fields")
    void toDomain() {
        UserEntity e = new UserEntity();
        e.id = UUID.randomUUID();
        e.firstName = "Alice";
        e.lastName = "Martin";
        e.email = "alice@example.com";
        e.passwordHash = "hash";
        e.emailVerified = true;
        e.emailVerifiedAt = OffsetDateTime.now();
        e.createdAt = OffsetDateTime.now().minusDays(1);
        e.updatedAt = OffsetDateTime.now();

        User d = mapper.toDomain(e);

        assertThat(d.getId()).isEqualTo(e.id);
        assertThat(d.getEmail()).isEqualTo(e.email);
        assertThat(d.getPasswordHash()).isEqualTo(e.passwordHash);
        assertThat(d.isEmailVerified()).isTrue();
        assertThat(d.getCreatedAt()).isEqualTo(e.createdAt);
    }

    @Test
    @DisplayName("toEntity copies all fields")
    void toEntity() {
        User u = User.builder()
                .id(UUID.randomUUID()).firstName("Bob").lastName("D")
                .email("bob@b.com").passwordHash("h")
                .emailVerified(false).build();

        UserEntity e = mapper.toEntity(u);

        assertThat(e.id).isEqualTo(u.getId());
        assertThat(e.email).isEqualTo("bob@b.com");
        assertThat(e.emailVerified).isFalse();
    }

    @Test
    @DisplayName("updateEntity does not change id or createdAt")
    void updateEntityKeepsImmutableFields() {
        UserEntity e = new UserEntity();
        UUID originalId = UUID.randomUUID();
        OffsetDateTime originalCreatedAt = OffsetDateTime.now().minusDays(5);
        e.id = originalId;
        e.createdAt = originalCreatedAt;
        e.firstName = "old";

        User u = User.builder()
                .id(UUID.randomUUID())
                .firstName("new").lastName("L").email("e@e.com").passwordHash("h")
                .createdAt(OffsetDateTime.now()).build();

        mapper.updateEntity(e, u);

        assertThat(e.id).isEqualTo(originalId);
        assertThat(e.createdAt).isEqualTo(originalCreatedAt);
        assertThat(e.firstName).isEqualTo("new");
    }
}
