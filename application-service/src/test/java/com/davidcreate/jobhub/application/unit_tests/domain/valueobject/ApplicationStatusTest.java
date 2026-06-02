package com.davidcreate.jobhub.application.unit_tests.domain.valueobject;

import com.davidcreate.jobhub.application.domain.exception.ValidationException;
import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ApplicationStatus")
class ApplicationStatusTest {

    @Test
    @DisplayName("dbValue returns lowercase name")
    void dbValue() {
        assertThat(ApplicationStatus.APPLIED.dbValue()).isEqualTo("applied");
        assertThat(ApplicationStatus.WITHDRAWN.dbValue()).isEqualTo("withdrawn");
    }

    @Test
    @DisplayName("fromDbValue parses case-insensitively and trims")
    void fromDbValue() {
        assertThat(ApplicationStatus.fromDbValue(" Applied ")).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(ApplicationStatus.fromDbValue("REJECTED")).isEqualTo(ApplicationStatus.REJECTED);
    }

    @Test
    @DisplayName("fromDbValue rejects null")
    void rejectsNull() {
        assertThatThrownBy(() -> ApplicationStatus.fromDbValue(null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("fromDbValue rejects unknown values")
    void rejectsUnknown() {
        assertThatThrownBy(() -> ApplicationStatus.fromDbValue("queued"))
                .isInstanceOf(ValidationException.class);
    }
}
