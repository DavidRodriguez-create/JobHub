package com.davidcreate.jobhub.notification.unit_tests.adapter.in.rest.dto;

import com.davidcreate.jobhub.notification.adapter.in.rest.dto.NotificationResponseDto;
import com.davidcreate.jobhub.notification.contract.model.NotificationCategory;
import com.davidcreate.jobhub.notification.contract.model.NotificationResponse;
import com.davidcreate.jobhub.notification.domain.model.Notification;
import com.davidcreate.jobhub.notification.domain.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("NotificationResponseDto Unit Tests")
class NotificationResponseDtoTest {

    // TC-B-U-01
    @Test
    @DisplayName("TC-B-U-01: applicationId present is carried through to the response")
    void applicationIdPresentIsCarriedThrough() {
        UUID applicationId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .type(NotificationType.GHOSTED_ALERT)
                .title("Application marked as Ghosted")
                .message("Your application to Acme Corp has been marked as Ghosted after 14 days of silence")
                .read(false)
                .createdAt(LocalDateTime.now())
                .applicationId(applicationId)
                .build();

        NotificationResponse response = NotificationResponseDto.from(notification);

        assertThat(response.getApplicationId()).isEqualTo(applicationId);
    }

    // TC-B-U-02
    @Test
    @DisplayName("TC-B-U-02: applicationId null (SYSTEM) maps to null, not omitted/blown up")
    void applicationIdNullForSystemMapsToNull() {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .type(NotificationType.SYSTEM)
                .title("Welcome to JobHub")
                .message("Thanks for joining JobHub")
                .read(false)
                .createdAt(LocalDateTime.now())
                .applicationId(null)
                .build();

        NotificationResponse response = assertDoesNotThrow(() -> NotificationResponseDto.from(notification));

        assertThat(response.getApplicationId()).isNull();
    }

    // TC-B-U-03
    @Test
    @DisplayName("TC-B-U-03: applicationId null (SECURITY_RECOMMENDATION) maps to null")
    void applicationIdNullForSecurityRecommendationMapsToNull() {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .type(NotificationType.SECURITY_RECOMMENDATION)
                .title("Enable two-factor authentication")
                .message("Protect your account with 2FA")
                .read(false)
                .createdAt(LocalDateTime.now())
                .applicationId(null)
                .build();

        NotificationResponse response = NotificationResponseDto.from(notification);

        assertThat(response.getApplicationId()).isNull();
    }

    // TC-B-U-04
    @Test
    @DisplayName("TC-B-U-04: applicationId present for CUSTOM_REMINDER type is carried through")
    void applicationIdPresentForCustomReminderIsCarriedThrough() {
        UUID applicationId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .type(NotificationType.CUSTOM_REMINDER)
                .title("Prep for interview")
                .message("Prep for Acme Corp interview")
                .read(false)
                .createdAt(LocalDateTime.now())
                .applicationId(applicationId)
                .build();

        NotificationResponse response = NotificationResponseDto.from(notification);

        assertThat(response.getApplicationId()).isEqualTo(applicationId);
    }

    // TC-B-U-05
    @Test
    @DisplayName("TC-B-U-05: applicationId present for INTERVIEW_REMINDER type is carried through")
    void applicationIdPresentForInterviewReminderIsCarriedThrough() {
        UUID applicationId = UUID.randomUUID();
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .type(NotificationType.INTERVIEW_REMINDER)
                .title("Interview reminder")
                .message("Your interview is tomorrow at Acme Corp")
                .read(false)
                .createdAt(LocalDateTime.now())
                .applicationId(applicationId)
                .build();

        NotificationResponse response = NotificationResponseDto.from(notification);

        assertThat(response.getApplicationId()).isEqualTo(applicationId);
    }

    // TC-B-U-06
    @Test
    @DisplayName("TC-B-U-06: from() still carries all pre-existing fields unchanged alongside applicationId")
    void fromCarriesAllPreExistingFieldsUnchanged() {
        UUID id = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().withNano(0);
        Notification notification = Notification.builder()
                .id(id)
                .userId(UUID.randomUUID())
                .type(NotificationType.GHOSTED_ALERT)
                .title("Application marked as Ghosted")
                .message("Your application to Acme Corp has been marked as Ghosted after 14 days of silence")
                .read(true)
                .createdAt(createdAt)
                .applicationId(applicationId)
                .build();

        NotificationResponse response = NotificationResponseDto.from(notification);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getType().name()).isEqualTo("GHOSTED_ALERT");
        assertThat(response.getTitle()).isEqualTo("Application marked as Ghosted");
        assertThat(response.getMessage()).isEqualTo("Your application to Acme Corp has been marked as Ghosted after 14 days of silence");
        assertThat(response.getRead()).isTrue();
        assertThat(response.getCreatedAt()).isEqualTo(createdAt.atOffset(ZoneOffset.UTC));
        assertThat(response.getApplicationId()).isEqualTo(applicationId);
    }

    // NS-U-01
    @Test
    @DisplayName("NS-U-01: notification carrying a resolved company/jobTitle maps both onto the response")
    void resolvedCompanyAndJobTitleAreCarriedThrough() {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .type(NotificationType.GHOSTED_ALERT)
                .title("Application marked as Ghosted")
                .message("Your application to Acme Corp has been marked as Ghosted after 14 days of silence")
                .read(false)
                .createdAt(LocalDateTime.now())
                .applicationId(UUID.randomUUID())
                .company("Acme Corp")
                .jobTitle("Senior Backend Engineer")
                .build();

        NotificationResponse response = NotificationResponseDto.from(notification);

        assertThat(response.getCompany()).isEqualTo("Acme Corp");
        assertThat(response.getJobTitle()).isEqualTo("Senior Backend Engineer");
    }

    // NS-U-02
    @Test
    @DisplayName("NS-U-02: no resolved summary (unset company/jobTitle) maps to null, no exception")
    void unresolvedSummaryMapsToNullCompanyAndJobTitle() {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .type(NotificationType.GHOSTED_ALERT)
                .title("Application marked as Ghosted")
                .message("Your application to Acme Corp has been marked as Ghosted after 14 days of silence")
                .read(false)
                .createdAt(LocalDateTime.now())
                .applicationId(UUID.randomUUID())
                .build();

        NotificationResponse response = assertDoesNotThrow(() -> NotificationResponseDto.from(notification));

        assertThat(response.getCompany()).isNull();
        assertThat(response.getJobTitle()).isNull();
    }

    // NS-U-03
    @Test
    @DisplayName("NS-U-03: applicationId null (SYSTEM/SECURITY_RECOMMENDATION) never carries company/jobTitle")
    void nullApplicationIdNeverCarriesCompanyOrJobTitle() {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .type(NotificationType.SYSTEM)
                .title("Welcome to JobHub")
                .message("Thanks for joining JobHub")
                .read(false)
                .createdAt(LocalDateTime.now())
                .applicationId(null)
                .build();

        NotificationResponse response = NotificationResponseDto.from(notification);

        assertThat(response.getApplicationId()).isNull();
        assertThat(response.getCompany()).isNull();
        assertThat(response.getJobTitle()).isNull();
    }

    // NS-U-04
    @Test
    @DisplayName("NS-U-04: enrichment fields are independent of pre-existing fields (regression alongside TC-B-U-06)")
    void enrichmentDoesNotAffectPreExistingFields() {
        UUID id = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().withNano(0);
        Notification notification = Notification.builder()
                .id(id)
                .userId(UUID.randomUUID())
                .type(NotificationType.GHOSTED_ALERT)
                .title("Application marked as Ghosted")
                .message("Your application to Acme Corp has been marked as Ghosted after 14 days of silence")
                .read(true)
                .createdAt(createdAt)
                .applicationId(applicationId)
                .company("Acme Corp")
                .jobTitle("Senior Backend Engineer")
                .build();

        NotificationResponse response = NotificationResponseDto.from(notification);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getType().name()).isEqualTo("GHOSTED_ALERT");
        assertThat(response.getTitle()).isEqualTo("Application marked as Ghosted");
        assertThat(response.getMessage()).isEqualTo("Your application to Acme Corp has been marked as Ghosted after 14 days of silence");
        assertThat(response.getRead()).isTrue();
        assertThat(response.getCreatedAt()).isEqualTo(createdAt.atOffset(ZoneOffset.UTC));
        assertThat(response.getApplicationId()).isEqualTo(applicationId);
        assertThat(response.getCompany()).isEqualTo("Acme Corp");
        assertThat(response.getJobTitle()).isEqualTo("Senior Backend Engineer");
    }

    // NS244-U-04
    @Test
    @DisplayName("NS244-U-04: from(notification) where companyLogoUrl is populated sets the response field to the same value")
    void fromPopulatesCompanyLogoUrlWhenSet() {
        java.net.URI logoUrl = java.net.URI.create("https://cdn.example.com/acme.png");
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .type(NotificationType.GHOSTED_ALERT)
                .title("A wild ghost appeared!")
                .message("Your application has been marked as Ghosted")
                .read(false)
                .createdAt(LocalDateTime.now())
                .applicationId(UUID.randomUUID())
                .company("Acme Corp")
                .jobTitle("Senior Backend Engineer")
                .companyLogoUrl(logoUrl)
                .build();

        NotificationResponse response = NotificationResponseDto.from(notification);

        assertThat(response.getCompanyLogoUrl()).isEqualTo(logoUrl);
    }

    // NS244-U-05
    @Test
    @DisplayName("NS244-U-05: from(notification) where companyLogoUrl is null sets response field to null, other fields unaffected")
    void fromNullCompanyLogoUrlSetsNullOnResponse() {
        UUID id = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().withNano(0);
        Notification notification = Notification.builder()
                .id(id)
                .userId(UUID.randomUUID())
                .type(NotificationType.GHOSTED_ALERT)
                .title("Application marked as Ghosted")
                .message("Your application to Acme Corp has been marked as Ghosted after 14 days of silence")
                .read(true)
                .createdAt(createdAt)
                .applicationId(applicationId)
                .company("Acme Corp")
                .jobTitle("Senior Backend Engineer")
                .companyLogoUrl(null)
                .build();

        NotificationResponse response = assertDoesNotThrow(() -> NotificationResponseDto.from(notification));

        assertThat(response.getCompanyLogoUrl()).isNull();
        // regression: other fields unaffected
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getApplicationId()).isEqualTo(applicationId);
        assertThat(response.getCompany()).isEqualTo("Acme Corp");
        assertThat(response.getJobTitle()).isEqualTo("Senior Backend Engineer");
    }

    // NS244-U-06
    @Test
    @DisplayName("NS244-U-06: from(notification) for SYSTEM (applicationId==null) never has companyLogoUrl - consistent with company/jobTitle null")
    void fromSystemNotificationHasNullCompanyLogoUrl() {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .type(NotificationType.SYSTEM)
                .title("Welcome to JobHub")
                .message("Thanks for joining JobHub")
                .read(false)
                .createdAt(LocalDateTime.now())
                .applicationId(null)
                .build();

        NotificationResponse response = NotificationResponseDto.from(notification);

        assertThat(response.getApplicationId()).isNull();
        assertThat(response.getCompany()).isNull();
        assertThat(response.getJobTitle()).isNull();
        assertThat(response.getCompanyLogoUrl()).isNull();
    }

    // TC-439-10
    @Test
    @DisplayName("TC-439-10: from(INTERVIEW_REMINDER) maps category to APPLICATION")
    void fromInterviewReminderMapsCategoryToApplication() {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .type(NotificationType.INTERVIEW_REMINDER)
                .title("Interview reminder")
                .message("Your interview is tomorrow at Acme Corp")
                .read(false)
                .createdAt(LocalDateTime.now())
                .applicationId(UUID.randomUUID())
                .build();

        NotificationResponse response = NotificationResponseDto.from(notification);

        assertThat(response.getCategory()).isEqualTo(NotificationCategory.APPLICATION);
    }

    // TC-439-11
    @Test
    @DisplayName("TC-439-11: from(SECURITY_RECOMMENDATION, no applicationId) maps category to ACCOUNT")
    void fromSecurityRecommendationMapsCategoryToAccount() {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .type(NotificationType.SECURITY_RECOMMENDATION)
                .title("Enable two-factor authentication")
                .message("Protect your account with 2FA")
                .read(false)
                .createdAt(LocalDateTime.now())
                .applicationId(null)
                .build();

        NotificationResponse response = NotificationResponseDto.from(notification);

        assertThat(response.getCategory()).isEqualTo(NotificationCategory.ACCOUNT);
        assertThat(response.getApplicationId()).isNull();
    }

    // TC-439-12
    @Test
    @DisplayName("TC-439-12: from() maps category correctly for every NotificationType value")
    void fromMapsCategoryCorrectlyForEveryType() {
        assertCategoryFor(NotificationType.INTERVIEW_REMINDER, NotificationCategory.APPLICATION);
        assertCategoryFor(NotificationType.GHOSTED_ALERT, NotificationCategory.APPLICATION);
        assertCategoryFor(NotificationType.APPLICATION_UPDATE, NotificationCategory.APPLICATION);
        assertCategoryFor(NotificationType.CUSTOM_REMINDER, NotificationCategory.APPLICATION);
        assertCategoryFor(NotificationType.SECURITY_RECOMMENDATION, NotificationCategory.ACCOUNT);
        assertCategoryFor(NotificationType.SYSTEM, NotificationCategory.ACCOUNT);
    }

    private static void assertCategoryFor(NotificationType type, NotificationCategory expected) {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .type(type)
                .title("Title")
                .message("Message")
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();

        NotificationResponse response = NotificationResponseDto.from(notification);

        assertThat(response.getCategory()).as("category for %s", type).isEqualTo(expected);
    }

    // TC-439-13
    @Test
    @DisplayName("TC-439-13: category is populated on a fully-populated Notification without disturbing any other mapped field (regression alongside TC-B-U-06/NS-U-04/NS244-U-05)")
    void fromFullyPopulatedNotificationPopulatesCategoryWithoutDisturbingOtherFields() {
        UUID id = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().withNano(0);
        java.net.URI logoUrl = java.net.URI.create("https://cdn.example.com/acme.png");
        Notification notification = Notification.builder()
                .id(id)
                .userId(UUID.randomUUID())
                .type(NotificationType.GHOSTED_ALERT)
                .title("Application marked as Ghosted")
                .message("Your application to Acme Corp has been marked as Ghosted after 14 days of silence")
                .read(true)
                .createdAt(createdAt)
                .applicationId(applicationId)
                .company("Acme Corp")
                .jobTitle("Senior Backend Engineer")
                .companyLogoUrl(logoUrl)
                .build();

        NotificationResponse response = NotificationResponseDto.from(notification);

        assertThat(response.getCategory()).isEqualTo(NotificationCategory.APPLICATION);
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getType().name()).isEqualTo("GHOSTED_ALERT");
        assertThat(response.getTitle()).isEqualTo("Application marked as Ghosted");
        assertThat(response.getMessage()).isEqualTo("Your application to Acme Corp has been marked as Ghosted after 14 days of silence");
        assertThat(response.getRead()).isTrue();
        assertThat(response.getCreatedAt()).isEqualTo(createdAt.atOffset(ZoneOffset.UTC));
        assertThat(response.getApplicationId()).isEqualTo(applicationId);
        assertThat(response.getCompany()).isEqualTo("Acme Corp");
        assertThat(response.getJobTitle()).isEqualTo("Senior Backend Engineer");
        assertThat(response.getCompanyLogoUrl()).isEqualTo(logoUrl);
    }
}
