package com.davidcreate.jobhub.auth.unit_tests.domain.valueobject;

import com.davidcreate.jobhub.auth.domain.exception.ValidationException;
import com.davidcreate.jobhub.auth.domain.valueobject.Email;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Email value object")
class EmailTest {

    @Test
    @DisplayName("normalises to lowercase and trims")
    void normalises() {
        assertThat(Email.of("  Alice@Example.COM ").value()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("rejects blank")
    void rejectsBlank() {
        assertThatThrownBy(() -> Email.of("   ")).isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("rejects invalid format")
    void rejectsInvalid() {
        assertThatThrownBy(() -> Email.of("not-an-email")).isInstanceOf(ValidationException.class);
    }
}
