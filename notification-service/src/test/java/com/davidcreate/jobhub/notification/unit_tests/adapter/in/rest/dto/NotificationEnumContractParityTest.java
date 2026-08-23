package com.davidcreate.jobhub.notification.unit_tests.adapter.in.rest.dto;

import com.davidcreate.jobhub.notification.domain.model.NotificationCategory;
import com.davidcreate.jobhub.notification.domain.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Architect follow-up O-3 from story #439.
 *
 * NotificationResponseDto.from() translates both enums by name, via
 * contract.NotificationType.valueOf(domain.getType().name()) and
 * contract.NotificationCategory.valueOf(domain...category().name()). Those calls throw
 * IllegalArgumentException at runtime, on every list request, if a constant is ever added
 * or renamed on one side only. Nothing in the compiler or the generator links the two
 * enums, so this test is the link: it fails at build time instead.
 */
@DisplayName("Domain to contract enum parity Unit Tests")
class NotificationEnumContractParityTest {

    private static Set<String> namesOf(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("TC-439-42: domain and contract NotificationType declare exactly the same constant names")
    void notificationTypeNameSetsMatch() {
        assertThat(namesOf(NotificationType.values()))
                .as("domain NotificationType vs contract NotificationType")
                .isEqualTo(namesOf(com.davidcreate.jobhub.notification.contract.model.NotificationType.values()));
    }

    @Test
    @DisplayName("TC-439-43: domain and contract NotificationCategory declare exactly the same constant names")
    void notificationCategoryNameSetsMatch() {
        assertThat(namesOf(NotificationCategory.values()))
                .as("domain NotificationCategory vs contract NotificationCategory")
                .isEqualTo(namesOf(com.davidcreate.jobhub.notification.contract.model.NotificationCategory.values()));
    }

    @Test
    @DisplayName("TC-439-44: every domain NotificationType survives the valueOf translation used by NotificationResponseDto")
    void everyDomainTypeTranslates() {
        for (NotificationType type : NotificationType.values()) {
            assertThatCode(() -> com.davidcreate.jobhub.notification.contract.model.NotificationType.valueOf(type.name()))
                    .as("translating %s", type)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("TC-439-45: every category derived from a NotificationType survives the valueOf translation")
    void everyDerivedCategoryTranslates() {
        for (NotificationType type : NotificationType.values()) {
            assertThatCode(() -> com.davidcreate.jobhub.notification.contract.model.NotificationCategory.valueOf(type.category().name()))
                    .as("translating the category of %s", type)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("TC-439-46: contract enums serialise on the wire under their own constant name")
    void contractEnumWireValuesMatchConstantNames() {
        for (var type : com.davidcreate.jobhub.notification.contract.model.NotificationType.values()) {
            assertThat(type.toString()).as("wire value of %s", type.name()).isEqualTo(type.name());
        }
        for (var category : com.davidcreate.jobhub.notification.contract.model.NotificationCategory.values()) {
            assertThat(category.toString()).as("wire value of %s", category.name()).isEqualTo(category.name());
        }
    }
}
