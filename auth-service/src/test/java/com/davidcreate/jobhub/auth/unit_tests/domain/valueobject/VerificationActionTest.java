package com.davidcreate.jobhub.auth.unit_tests.domain.valueobject;

import com.davidcreate.jobhub.auth.domain.exception.ValidationException;
import com.davidcreate.jobhub.auth.domain.valueobject.VerificationAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EV-U-01: VerificationAction.VERIFY_EMAIL exists and carries value "verify-email"
 * EV-U-02: fromValue("verify-email") returns VERIFY_EMAIL
 */
@DisplayName("VerificationAction — EV-U-01, EV-U-02")
class VerificationActionTest {

    @Test
    @DisplayName("EV-U-01: VERIFY_EMAIL constant carries value 'verify-email'")
    void verifyEmailConstantExists() {
        assertThat(VerificationAction.VERIFY_EMAIL).isNotNull();
        assertThat(VerificationAction.VERIFY_EMAIL.value()).isEqualTo("verify-email");
    }

    @Test
    @DisplayName("EV-U-02: fromValue('verify-email') returns VERIFY_EMAIL")
    void fromValueVerifyEmail() {
        assertThat(VerificationAction.fromValue("verify-email")).isEqualTo(VerificationAction.VERIFY_EMAIL);
    }

    @Test
    @DisplayName("fromValue with unknown value throws ValidationException")
    void fromValueUnknown() {
        assertThatThrownBy(() -> VerificationAction.fromValue("unknown"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("existing actions still present")
    void existingActionsPresent() {
        assertThat(VerificationAction.fromValue("delete-account")).isEqualTo(VerificationAction.DELETE_ACCOUNT);
        assertThat(VerificationAction.fromValue("delete-all-applications")).isEqualTo(VerificationAction.DELETE_ALL_APPLICATIONS);
    }
}
