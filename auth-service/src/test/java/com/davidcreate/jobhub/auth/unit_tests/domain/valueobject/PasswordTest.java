package com.davidcreate.jobhub.auth.unit_tests.domain.valueobject;

import com.davidcreate.jobhub.auth.domain.exception.ValidationException;
import com.davidcreate.jobhub.auth.domain.valueobject.Password;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Password value object")
class PasswordTest {

    @Test
    @DisplayName("accepts at minimum length")
    void acceptsValid() {
        assertThat(Password.of("12345678").raw()).isEqualTo("12345678");
    }

    @Test
    @DisplayName("rejects too short")
    void rejectsShort() {
        assertThatThrownBy(() -> Password.of("short"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at least 8");
    }

    @Test
    @DisplayName("rejects null")
    void rejectsNull() {
        assertThatThrownBy(() -> Password.of(null)).isInstanceOf(ValidationException.class);
    }
}
