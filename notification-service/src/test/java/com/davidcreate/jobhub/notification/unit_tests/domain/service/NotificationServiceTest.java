package com.davidcreate.jobhub.notification.unit_tests.domain.service;

import com.davidcreate.jobhub.notification.domain.exception.NotificationNotFoundException;
import com.davidcreate.jobhub.notification.domain.model.ApplicationSummary;
import com.davidcreate.jobhub.notification.domain.model.Notification;
import com.davidcreate.jobhub.notification.domain.model.NotificationPage;
import com.davidcreate.jobhub.notification.domain.model.NotificationType;
import com.davidcreate.jobhub.notification.domain.model.ReadStatusFilter;
import com.davidcreate.jobhub.notification.domain.port.out.ApplicationSummaryGateway;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationRepository;
import com.davidcreate.jobhub.notification.domain.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Unit Tests")
class NotificationServiceTest {

    @Mock NotificationRepository repository;
    @Mock ApplicationSummaryGateway summaryGateway;
    @InjectMocks NotificationService service;

    private final UUID userId = UUID.randomUUID();

    private Notification notification(boolean read, LocalDateTime createdAt) {
        return Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .type(NotificationType.SYSTEM)
                .title("title")
                .message("message")
                .read(read)
                .createdAt(createdAt)
                .build();
    }

    private Notification notificationWithApplication(UUID applicationId, LocalDateTime createdAt) {
        return Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .type(NotificationType.GHOSTED_ALERT)
                .title("title")
                .message("message")
                .read(false)
                .createdAt(createdAt)
                .applicationId(applicationId)
                .build();
    }

    // TC-B-U-01
    @Test
    @DisplayName("listNotifications returns a page mapped from the repository, newest first")
    void listNotificationsReturnsPageMappedFromRepository() {
        LocalDateTime now = LocalDateTime.now();
        List<Notification> notifications = List.of(
                notification(false, now),
                notification(false, now.minusMinutes(1)),
                notification(true, now.minusMinutes(2))
        );
        when(repository.findByUserId(userId, 0, 20, ReadStatusFilter.ALL)).thenReturn(notifications);
        when(repository.countByUserId(userId, ReadStatusFilter.ALL)).thenReturn(3L);

        NotificationPage result = service.listNotifications(userId, 0, 20, ReadStatusFilter.ALL);

        assertThat(result.getContent()).containsExactlyElementsOf(notifications);
        assertThat(result.getTotalElements()).isEqualTo(3L);
        assertThat(result.getPage()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getTotalPages()).isEqualTo(1);
    }

    // TC-B-U-02
    @Test
    @DisplayName("listNotifications computes totalPages correctly when totalElements exceeds size")
    void listNotificationsComputesTotalPagesWhenExceedingSize() {
        List<Notification> page = List.of(
                notification(false, LocalDateTime.now()),
                notification(false, LocalDateTime.now().minusMinutes(1))
        );
        when(repository.findByUserId(userId, 0, 20, ReadStatusFilter.ALL)).thenReturn(
                List.copyOf(java.util.stream.Stream.generate(() -> notification(false, LocalDateTime.now()))
                        .limit(20).toList())
        );
        when(repository.countByUserId(userId, ReadStatusFilter.ALL)).thenReturn(25L);

        NotificationPage result = service.listNotifications(userId, 0, 20, ReadStatusFilter.ALL);

        assertThat(result.getTotalElements()).isEqualTo(25L);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(20);
    }

    // TC-B-U-03
    @Test
    @DisplayName("listNotifications passes readStatus=UNREAD filter through to the repository")
    void listNotificationsPassesUnreadFilterThrough() {
        List<Notification> unread = List.of(notification(false, LocalDateTime.now()), notification(false, LocalDateTime.now().minusMinutes(1)));
        when(repository.findByUserId(userId, 0, 20, ReadStatusFilter.UNREAD)).thenReturn(unread);
        when(repository.countByUserId(userId, ReadStatusFilter.UNREAD)).thenReturn(2L);

        NotificationPage result = service.listNotifications(userId, 0, 20, ReadStatusFilter.UNREAD);

        verify(repository).findByUserId(eq(userId), eq(0), eq(20), eq(ReadStatusFilter.UNREAD));
        assertThat(result.getContent()).allMatch(n -> !n.isRead());
    }

    // TC-B-U-04
    @Test
    @DisplayName("listNotifications passes readStatus=READ filter through to the repository")
    void listNotificationsPassesReadFilterThrough() {
        List<Notification> read = List.of(notification(true, LocalDateTime.now()), notification(true, LocalDateTime.now().minusMinutes(1)));
        when(repository.findByUserId(userId, 0, 20, ReadStatusFilter.READ)).thenReturn(read);
        when(repository.countByUserId(userId, ReadStatusFilter.READ)).thenReturn(2L);

        NotificationPage result = service.listNotifications(userId, 0, 20, ReadStatusFilter.READ);

        verify(repository).findByUserId(eq(userId), eq(0), eq(20), eq(ReadStatusFilter.READ));
        assertThat(result.getContent()).allMatch(Notification::isRead);
    }

    // TC-B-U-05
    @Test
    @DisplayName("listNotifications returns an empty page when the user has no notifications")
    void listNotificationsReturnsEmptyPageForUserWithNoNotifications() {
        when(repository.findByUserId(userId, 0, 20, ReadStatusFilter.ALL)).thenReturn(List.of());
        when(repository.countByUserId(userId, ReadStatusFilter.ALL)).thenReturn(0L);

        NotificationPage result = service.listNotifications(userId, 0, 20, ReadStatusFilter.ALL);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0L);
        assertThat(result.getTotalPages()).isEqualTo(0);
    }

    // TC-B-U-06
    @Test
    @DisplayName("getUnreadCount returns the repository's unread count for the user")
    void getUnreadCountReturnsRepositoryCount() {
        when(repository.countByUserId(userId, ReadStatusFilter.UNREAD)).thenReturn(5L);

        long result = service.getUnreadCount(userId);

        assertThat(result).isEqualTo(5L);
    }

    // TC-B-U-07
    @Test
    @DisplayName("getUnreadCount returns zero when the user has no unread notifications")
    void getUnreadCountReturnsZeroWhenNoneUnread() {
        when(repository.countByUserId(userId, ReadStatusFilter.UNREAD)).thenReturn(0L);

        long result = service.getUnreadCount(userId);

        assertThat(result).isEqualTo(0L);
    }

    // TC-B-U-08
    @Test
    @DisplayName("markNotificationRead marks an existing, owned, unread notification as read")
    void markNotificationReadMarksUnreadNotificationAsRead() {
        UUID notificationId = UUID.randomUUID();
        Notification unread = notification(false, LocalDateTime.now());
        when(repository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.of(unread));

        service.markNotificationRead(userId, notificationId);

        verify(repository, times(1)).markRead(unread.getId());
    }

    // TC-B-U-09
    @Test
    @DisplayName("markNotificationRead on an already-read notification is a no-op (idempotent)")
    void markNotificationReadOnAlreadyReadIsIdempotent() {
        UUID notificationId = UUID.randomUUID();
        Notification alreadyRead = notification(true, LocalDateTime.now());
        when(repository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.of(alreadyRead));

        service.markNotificationRead(userId, notificationId);
        // No exception thrown — completes successfully regardless of whether markRead is called.
    }

    // TC-B-U-10
    @Test
    @DisplayName("markNotificationRead throws NotificationNotFoundException when the notification does not exist")
    void markNotificationReadThrowsWhenNotFound() {
        UUID notificationId = UUID.randomUUID();
        when(repository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markNotificationRead(userId, notificationId))
                .isInstanceOf(NotificationNotFoundException.class);

        verify(repository, never()).markRead(any());
    }

    // TC-B-U-11
    @Test
    @DisplayName("markNotificationRead throws NotificationNotFoundException when the notification belongs to another user")
    void markNotificationReadThrowsWhenNotOwnedByUser() {
        UUID notificationId = UUID.randomUUID();
        when(repository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markNotificationRead(userId, notificationId))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    // TC-B-U-12
    @Test
    @DisplayName("markAllNotificationsRead marks every unread notification owned by the user as read")
    void markAllNotificationsReadMarksAllUnread() {
        service.markAllNotificationsRead(userId);

        verify(repository, times(1)).markAllRead(userId);
    }

    // TC-B-U-13
    @Test
    @DisplayName("markAllNotificationsRead is idempotent when nothing is unread")
    void markAllNotificationsReadIsIdempotentWhenNothingUnread() {
        service.markAllNotificationsRead(userId);

        verify(repository, times(1)).markAllRead(userId);
    }

    // ── Enrich-at-read (ADR 0014, story #207) ───────────────────────────────────

    // NS-U-05
    @Test
    @DisplayName("NS-U-05: listing a page collects the distinct non-null applicationIds and calls the gateway once")
    void listNotificationsCallsGatewayOnceWithDistinctApplicationIds() {
        UUID appA = UUID.randomUUID();
        UUID appB = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        List<Notification> page = List.of(
                notificationWithApplication(appA, now),
                notificationWithApplication(appA, now.minusMinutes(1)),
                notificationWithApplication(appB, now.minusMinutes(2)),
                notification(false, now.minusMinutes(3))
        );
        when(repository.findByUserId(userId, 0, 20, ReadStatusFilter.ALL)).thenReturn(page);
        when(repository.countByUserId(userId, ReadStatusFilter.ALL)).thenReturn(4L);
        when(summaryGateway.resolve(any())).thenReturn(Map.of());

        service.listNotifications(userId, 0, 20, ReadStatusFilter.ALL);

        verify(summaryGateway, times(1)).resolve(eq(Set.of(appA, appB)));
    }

    // NS-U-06
    @Test
    @DisplayName("NS-U-06: gateway resolves every requested id, each notification gets its company/jobTitle populated")
    void listNotificationsPopulatesCompanyAndJobTitleWhenAllResolve() {
        UUID appA = UUID.randomUUID();
        UUID appB = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Notification notifA = notificationWithApplication(appA, now);
        Notification notifB = notificationWithApplication(appB, now.minusMinutes(1));
        when(repository.findByUserId(userId, 0, 20, ReadStatusFilter.ALL)).thenReturn(List.of(notifA, notifB));
        when(repository.countByUserId(userId, ReadStatusFilter.ALL)).thenReturn(2L);
        when(summaryGateway.resolve(eq(Set.of(appA, appB)))).thenReturn(Map.of(
                appA, ApplicationSummary.builder().applicationId(appA).company("Acme Corp").jobTitle("Backend Engineer").build(),
                appB, ApplicationSummary.builder().applicationId(appB).company("Globex").jobTitle("QA Lead").build()
        ));

        NotificationPage result = service.listNotifications(userId, 0, 20, ReadStatusFilter.ALL);

        Notification resolvedA = result.getContent().stream().filter(n -> n.getId().equals(notifA.getId())).findFirst().orElseThrow();
        Notification resolvedB = result.getContent().stream().filter(n -> n.getId().equals(notifB.getId())).findFirst().orElseThrow();
        assertThat(resolvedA.getCompany()).isEqualTo("Acme Corp");
        assertThat(resolvedA.getJobTitle()).isEqualTo("Backend Engineer");
        assertThat(resolvedB.getCompany()).isEqualTo("Globex");
        assertThat(resolvedB.getJobTitle()).isEqualTo("QA Lead");
    }

    // NS-U-07
    @Test
    @DisplayName("NS-U-07: gateway returns a partial result, omitted-id notifications get null company/jobTitle, others populate normally")
    void listNotificationsHandlesPartialGatewayResult() {
        UUID appResolved = UUID.randomUUID();
        UUID appUnresolved = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Notification resolvedNotif = notificationWithApplication(appResolved, now);
        Notification unresolvedNotif = notificationWithApplication(appUnresolved, now.minusMinutes(1));
        when(repository.findByUserId(userId, 0, 20, ReadStatusFilter.ALL)).thenReturn(List.of(resolvedNotif, unresolvedNotif));
        when(repository.countByUserId(userId, ReadStatusFilter.ALL)).thenReturn(2L);
        when(summaryGateway.resolve(eq(Set.of(appResolved, appUnresolved)))).thenReturn(Map.of(
                appResolved, ApplicationSummary.builder().applicationId(appResolved).company("Acme Corp").jobTitle("Backend Engineer").build()
        ));

        NotificationPage result = service.listNotifications(userId, 0, 20, ReadStatusFilter.ALL);

        Notification resolved = result.getContent().stream().filter(n -> n.getId().equals(resolvedNotif.getId())).findFirst().orElseThrow();
        Notification unresolved = result.getContent().stream().filter(n -> n.getId().equals(unresolvedNotif.getId())).findFirst().orElseThrow();
        assertThat(resolved.getCompany()).isEqualTo("Acme Corp");
        assertThat(resolved.getJobTitle()).isEqualTo("Backend Engineer");
        assertThat(unresolved.getCompany()).isNull();
        assertThat(unresolved.getJobTitle()).isNull();
    }

    // NS-U-08
    @Test
    @DisplayName("NS-U-08: gateway returns an empty result for the whole batch, every entry gets null company/jobTitle, page still returned")
    void listNotificationsHandlesEmptyGatewayResult() {
        UUID appA = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Notification notifA = notificationWithApplication(appA, now);
        when(repository.findByUserId(userId, 0, 20, ReadStatusFilter.ALL)).thenReturn(List.of(notifA));
        when(repository.countByUserId(userId, ReadStatusFilter.ALL)).thenReturn(1L);
        when(summaryGateway.resolve(eq(Set.of(appA)))).thenReturn(Map.of());

        NotificationPage result = service.listNotifications(userId, 0, 20, ReadStatusFilter.ALL);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCompany()).isNull();
        assertThat(result.getContent().get(0).getJobTitle()).isNull();
    }

    // NS-U-09
    @Test
    @DisplayName("NS-U-09: a page with zero non-null applicationIds never calls the gateway")
    void listNotificationsNeverCallsGatewayWhenNoApplicationIds() {
        LocalDateTime now = LocalDateTime.now();
        List<Notification> page = List.of(notification(false, now), notification(true, now.minusMinutes(1)));
        when(repository.findByUserId(userId, 0, 20, ReadStatusFilter.ALL)).thenReturn(page);
        when(repository.countByUserId(userId, ReadStatusFilter.ALL)).thenReturn(2L);

        service.listNotifications(userId, 0, 20, ReadStatusFilter.ALL);

        verifyNoInteractions(summaryGateway);
    }

    // NS-U-10
    @Test
    @DisplayName("NS-U-10: gateway throws (upstream unavailable), the use case swallows it and returns the page with null company/jobTitle")
    void listNotificationsSwallowsGatewayFailureAndReturnsPage() {
        UUID appA = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Notification notifA = notificationWithApplication(appA, now);
        when(repository.findByUserId(userId, 0, 20, ReadStatusFilter.ALL)).thenReturn(List.of(notifA));
        when(repository.countByUserId(userId, ReadStatusFilter.ALL)).thenReturn(1L);
        when(summaryGateway.resolve(eq(Set.of(appA)))).thenThrow(new RuntimeException("upstream unavailable"));

        NotificationPage result = assertDoesNotThrowSafely(() -> service.listNotifications(userId, 0, 20, ReadStatusFilter.ALL));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCompany()).isNull();
        assertThat(result.getContent().get(0).getJobTitle()).isNull();
    }

    private static NotificationPage assertDoesNotThrowSafely(java.util.function.Supplier<NotificationPage> supplier) {
        return org.junit.jupiter.api.Assertions.assertDoesNotThrow(supplier::get);
    }

    // ── Delete notification (story #206) ─────────────────────────────────────────

    // TC-206-B-02 (TC-B-U-14)
    @Test
    @DisplayName("TC-206-B-02 (TC-B-U-14): delete completes when the repository deletes the owned row")
    void deleteCompletesWhenRepositoryDeletesOwnedRow() {
        UUID notificationId = UUID.randomUUID();
        when(repository.deleteByIdAndUser(notificationId, userId)).thenReturn(true);

        service.delete(notificationId, userId);

        verify(repository, times(1)).deleteByIdAndUser(notificationId, userId);
    }

    // TC-206-B-03 (TC-B-U-15)
    @Test
    @DisplayName("TC-206-B-03 (TC-B-U-15): delete throws NotificationNotFoundException when the repository reports no row deleted")
    void deleteThrowsWhenRepositoryDeletesNothing() {
        UUID notificationId = UUID.randomUUID();
        when(repository.deleteByIdAndUser(notificationId, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(notificationId, userId))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    // ── companyLogoUrl threading (ADR 0015, story #244) ─────────────────────────

    // NS244-U-07
    @Test
    @DisplayName("NS244-U-07: gateway returns summary with companyLogoUrl populated: enriched notification carries all three fields")
    void enrichWithLogoUrlAllThreeFieldsPopulated() {
        UUID appA = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Notification notifA = notificationWithApplication(appA, now);
        when(repository.findByUserId(userId, 0, 20, ReadStatusFilter.ALL)).thenReturn(List.of(notifA));
        when(repository.countByUserId(userId, ReadStatusFilter.ALL)).thenReturn(1L);

        java.net.URI logoUrl = java.net.URI.create("https://cdn.example.com/acme.png");
        when(summaryGateway.resolve(any())).thenReturn(Map.of(
                appA, ApplicationSummary.builder()
                        .applicationId(appA)
                        .company("Acme Corp")
                        .jobTitle("Senior Backend Engineer")
                        .companyLogoUrl(logoUrl)
                        .build()
        ));

        NotificationPage result = service.listNotifications(userId, 0, 20, ReadStatusFilter.ALL);

        Notification enriched = result.getContent().get(0);
        assertThat(enriched.getCompany()).isEqualTo("Acme Corp");
        assertThat(enriched.getJobTitle()).isEqualTo("Senior Backend Engineer");
        assertThat(enriched.getCompanyLogoUrl()).isEqualTo(logoUrl);
    }

    // NS244-U-08
    @Test
    @DisplayName("NS244-U-08: gateway returns summary with companyLogoUrl=null but company/jobTitle populated: three fields are propagated independently")
    void enrichWithNullLogoUrlCompanyAndJobTitleStillPopulated() {
        UUID appA = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Notification notifA = notificationWithApplication(appA, now);
        when(repository.findByUserId(userId, 0, 20, ReadStatusFilter.ALL)).thenReturn(List.of(notifA));
        when(repository.countByUserId(userId, ReadStatusFilter.ALL)).thenReturn(1L);

        when(summaryGateway.resolve(any())).thenReturn(Map.of(
                appA, ApplicationSummary.builder()
                        .applicationId(appA)
                        .company("Foo Inc")
                        .jobTitle("Backend Dev")
                        .companyLogoUrl(null)
                        .build()
        ));

        NotificationPage result = service.listNotifications(userId, 0, 20, ReadStatusFilter.ALL);

        Notification enriched = result.getContent().get(0);
        assertThat(enriched.getCompanyLogoUrl()).isNull();
        assertThat(enriched.getCompany()).isEqualTo("Foo Inc");
        assertThat(enriched.getJobTitle()).isEqualTo("Backend Dev");
    }

    // NS244-U-09
    @Test
    @DisplayName("NS244-U-09: partial gateway result - omitted-id notifications get all three enrichment fields null")
    void enrichPartialResultOmittedIdGetsAllThreeNull() {
        UUID appResolved = UUID.randomUUID();
        UUID appUnresolved = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Notification resolvedNotif = notificationWithApplication(appResolved, now);
        Notification unresolvedNotif = notificationWithApplication(appUnresolved, now.minusMinutes(1));
        when(repository.findByUserId(userId, 0, 20, ReadStatusFilter.ALL)).thenReturn(List.of(resolvedNotif, unresolvedNotif));
        when(repository.countByUserId(userId, ReadStatusFilter.ALL)).thenReturn(2L);

        java.net.URI logoUrl = java.net.URI.create("https://cdn.example.com/acme.png");
        when(summaryGateway.resolve(any())).thenReturn(Map.of(
                appResolved, ApplicationSummary.builder()
                        .applicationId(appResolved)
                        .company("Acme Corp")
                        .jobTitle("Backend Engineer")
                        .companyLogoUrl(logoUrl)
                        .build()
        ));

        NotificationPage result = service.listNotifications(userId, 0, 20, ReadStatusFilter.ALL);

        Notification resolved = result.getContent().stream().filter(n -> n.getId().equals(resolvedNotif.getId())).findFirst().orElseThrow();
        Notification unresolved = result.getContent().stream().filter(n -> n.getId().equals(unresolvedNotif.getId())).findFirst().orElseThrow();

        assertThat(resolved.getCompany()).isEqualTo("Acme Corp");
        assertThat(resolved.getJobTitle()).isEqualTo("Backend Engineer");
        assertThat(resolved.getCompanyLogoUrl()).isEqualTo(logoUrl);

        assertThat(unresolved.getCompany()).isNull();
        assertThat(unresolved.getJobTitle()).isNull();
        assertThat(unresolved.getCompanyLogoUrl()).isNull();
    }

    // NS244-U-10
    @Test
    @DisplayName("NS244-U-10: gateway throws for whole batch: all three enrichment fields null, page still returned normally")
    void enrichGatewayThrowsAllThreeFieldsNullPageReturns() {
        UUID appA = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Notification notifA = notificationWithApplication(appA, now);
        when(repository.findByUserId(userId, 0, 20, ReadStatusFilter.ALL)).thenReturn(List.of(notifA));
        when(repository.countByUserId(userId, ReadStatusFilter.ALL)).thenReturn(1L);
        when(summaryGateway.resolve(any())).thenThrow(new RuntimeException("upstream unavailable"));

        NotificationPage result = assertDoesNotThrowSafely(() -> service.listNotifications(userId, 0, 20, ReadStatusFilter.ALL));

        assertThat(result.getContent()).hasSize(1);
        Notification enriched = result.getContent().get(0);
        assertThat(enriched.getCompany()).isNull();
        assertThat(enriched.getJobTitle()).isNull();
        assertThat(enriched.getCompanyLogoUrl()).isNull();
    }

    // NS244-U-11
    @Test
    @DisplayName("NS244-U-11: a page with zero non-null applicationIds never calls the gateway (regression, unaffected by new field)")
    void zeroApplicationIdsNeverCallsGatewayRegression() {
        LocalDateTime now = LocalDateTime.now();
        List<Notification> page = List.of(notification(false, now), notification(true, now.minusMinutes(1)));
        when(repository.findByUserId(userId, 0, 20, ReadStatusFilter.ALL)).thenReturn(page);
        when(repository.countByUserId(userId, ReadStatusFilter.ALL)).thenReturn(2L);

        service.listNotifications(userId, 0, 20, ReadStatusFilter.ALL);

        verifyNoInteractions(summaryGateway);
    }
}
