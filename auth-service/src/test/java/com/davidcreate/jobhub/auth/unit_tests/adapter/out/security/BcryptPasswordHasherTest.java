package com.davidcreate.jobhub.auth.unit_tests.adapter.out.security;

import com.davidcreate.jobhub.auth.adapter.out.security.BcryptPasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BcryptPasswordHasher Unit Tests")
class BcryptPasswordHasherTest {

    BcryptPasswordHasher hasher;

    @BeforeEach
    void setUp() throws Exception {
        hasher = new BcryptPasswordHasher();
        Field f = BcryptPasswordHasher.class.getDeclaredField("cost");
        f.setAccessible(true);
        f.setInt(hasher, 4);
    }

    @Test
    @DisplayName("hash and matches round-trip succeeds")
    void roundTrip() {
        String hash = hasher.hash("test1234");
        assertThat(hasher.matches("test1234", hash)).isTrue();
    }

    @Test
    @DisplayName("matches rejects wrong password")
    void rejectsWrong() {
        String hash = hasher.hash("test1234");
        assertThat(hasher.matches("wrong", hash)).isFalse();
    }

    @Test
    @DisplayName("each hash differs (salt randomness)")
    void hashesDiffer() {
        assertThat(hasher.hash("test1234")).isNotEqualTo(hasher.hash("test1234"));
    }
}
