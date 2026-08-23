package com.davidcreate.jobhub.notification.unit_tests.domain.model;

import com.davidcreate.jobhub.notification.domain.model.NotificationCategory;
import com.davidcreate.jobhub.notification.domain.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationType#category() Unit Tests")
class NotificationTypeCategoryTest {

    // TC-439-1
    @Test
    @DisplayName("TC-439-1: INTERVIEW_REMINDER derives to APPLICATION")
    void interviewReminderDerivesToApplication() {
        assertThat(NotificationType.INTERVIEW_REMINDER.category()).isEqualTo(NotificationCategory.APPLICATION);
    }

    // TC-439-2
    @Test
    @DisplayName("TC-439-2: GHOSTED_ALERT derives to APPLICATION")
    void ghostedAlertDerivesToApplication() {
        assertThat(NotificationType.GHOSTED_ALERT.category()).isEqualTo(NotificationCategory.APPLICATION);
    }

    // TC-439-3
    @Test
    @DisplayName("TC-439-3: APPLICATION_UPDATE derives to APPLICATION")
    void applicationUpdateDerivesToApplication() {
        assertThat(NotificationType.APPLICATION_UPDATE.category()).isEqualTo(NotificationCategory.APPLICATION);
    }

    // TC-439-4
    @Test
    @DisplayName("TC-439-4: CUSTOM_REMINDER derives to APPLICATION")
    void customReminderDerivesToApplication() {
        assertThat(NotificationType.CUSTOM_REMINDER.category()).isEqualTo(NotificationCategory.APPLICATION);
    }

    // TC-439-5
    @Test
    @DisplayName("TC-439-5: SECURITY_RECOMMENDATION derives to ACCOUNT")
    void securityRecommendationDerivesToAccount() {
        assertThat(NotificationType.SECURITY_RECOMMENDATION.category()).isEqualTo(NotificationCategory.ACCOUNT);
    }

    // TC-439-6
    @Test
    @DisplayName("TC-439-6: SYSTEM derives to ACCOUNT")
    void systemDerivesToAccount() {
        assertThat(NotificationType.SYSTEM.category()).isEqualTo(NotificationCategory.ACCOUNT);
    }

    // TC-439-7
    @Test
    @DisplayName("TC-439-7: every NotificationType value resolves to a non-null category (runtime companion to the compiler's exhaustiveness guarantee)")
    void everyTypeResolvesToNonNullCategory() {
        for (NotificationType type : NotificationType.values()) {
            assertThat(type.category()).as("category for %s", type).isNotNull();
        }
    }

    // TC-439-8
    @Test
    @DisplayName("TC-439-8: no existing type ever derives to JOB_POST (taxonomy-only, reserved)")
    void noExistingTypeDerivesToJobPost() {
        for (NotificationType type : NotificationType.values()) {
            assertThat(type.category()).as("category for %s", type).isNotEqualTo(NotificationCategory.JOB_POST);
        }
    }

    // TC-439-9
    @Test
    @DisplayName("TC-439-9: category() is a pure function, stable across repeated calls on the same constant")
    void categoryIsStableAcrossRepeatedCalls() {
        for (NotificationType type : NotificationType.values()) {
            assertThat(type.category()).as("category for %s", type).isEqualTo(type.category());
        }
    }
}
