package com.davidcreate.jobhub.notification.unit_tests.domain.service;

import com.davidcreate.jobhub.notification.domain.exception.ApplicationNotOwnedException;
import com.davidcreate.jobhub.notification.domain.exception.CustomReminderNotFoundException;
import com.davidcreate.jobhub.notification.domain.exception.CustomReminderNotScheduledException;
import com.davidcreate.jobhub.notification.domain.exception.CustomReminderTriggerInPastException;
import com.davidcreate.jobhub.notification.domain.model.CustomReminder;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderChannel;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderStage;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderStatus;
import com.davidcreate.jobhub.notification.domain.port.out.ApplicationOwnershipGateway;
import com.davidcreate.jobhub.notification.domain.port.out.CustomReminderRepository;
import com.davidcreate.jobhub.notification.domain.service.CustomReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomReminderService Unit Tests")
class CustomReminderServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-21T12:00:00Z"), ZoneOffset.UTC);

    @Mock CustomReminderRepository repository;
    @Mock ApplicationOwnershipGateway ownershipGateway;

    private CustomReminderService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();
    private final UUID reminderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CustomReminderService(repository, ownershipGateway, FIXED_CLOCK, 200, 2000);
    }

    private CustomReminder scheduled() {
        return CustomReminder.builder()
                .id(reminderId)
                .userId(userId)
                .applicationId(applicationId)
                .title("Old")
                .note("Note")
                .triggerAtUtc(Instant.now(FIXED_CLOCK).plusSeconds(3600))
                .channels(java.util.Set.of(CustomReminderChannel.IN_APP))
                .stage(CustomReminderStage.SCREENING)
                .status(CustomReminderStatus.SCHEDULED)
                .createdAt(Instant.now(FIXED_CLOCK))
                .updatedAt(Instant.now(FIXED_CLOCK))
                .build();
    }

    // CR-U-020
    @Test
    @DisplayName("CR-U-020: create happy path returns SCHEDULED reminder with all fields")
    void createHappyPath() {
        when(ownershipGateway.isOwnedByUser(applicationId, userId)).thenReturn(true);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant trigger = Instant.now(FIXED_CLOCK).plusSeconds(3600);
        CustomReminder result = service.create(userId, applicationId, "Prep notes", null, trigger,
                List.of(CustomReminderChannel.IN_APP, CustomReminderChannel.EMAIL), CustomReminderStage.INTERVIEW);

        assertThat(result.getStatus()).isEqualTo(CustomReminderStatus.SCHEDULED);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getApplicationId()).isEqualTo(applicationId);
        assertThat(result.getChannels()).containsExactlyInAnyOrder(CustomReminderChannel.IN_APP, CustomReminderChannel.EMAIL);
        assertThat(result.getStage()).isEqualTo(CustomReminderStage.INTERVIEW);
        verify(repository).save(any());
    }

    // CR-U-021
    @Test
    @DisplayName("CR-U-021: create with application not owned by user throws ApplicationNotOwnedException")
    void createApplicationNotOwned() {
        when(ownershipGateway.isOwnedByUser(applicationId, userId)).thenReturn(false);

        Instant trigger = Instant.now(FIXED_CLOCK).plusSeconds(3600);
        assertThatThrownBy(() -> service.create(userId, applicationId, "Prep notes", null, trigger,
                List.of(CustomReminderChannel.IN_APP), null))
                .isInstanceOf(ApplicationNotOwnedException.class);

        verify(repository, never()).save(any());
    }

    // CR-U-022
    @Test
    @DisplayName("CR-U-022: create with trigger in past throws before ownership check")
    void createTriggerInPastBeforeOwnershipCheck() {
        Instant past = Instant.now(FIXED_CLOCK).minusSeconds(60);

        assertThatThrownBy(() -> service.create(userId, applicationId, "Prep notes", null, past,
                List.of(CustomReminderChannel.IN_APP), null))
                .isInstanceOf(CustomReminderTriggerInPastException.class);

        verify(ownershipGateway, never()).isOwnedByUser(any(), any());
    }

    // CR-U-023
    @Test
    @DisplayName("CR-U-023: create normalises duplicate channels")
    void createNormalisesDuplicateChannels() {
        when(ownershipGateway.isOwnedByUser(applicationId, userId)).thenReturn(true);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant trigger = Instant.now(FIXED_CLOCK).plusSeconds(3600);
        CustomReminder result = service.create(userId, applicationId, "Prep notes", null, trigger,
                List.of(CustomReminderChannel.IN_APP, CustomReminderChannel.IN_APP), null);

        assertThat(result.getChannels()).containsExactly(CustomReminderChannel.IN_APP);
    }

    // CR-U-030
    @Test
    @DisplayName("CR-U-030: update happy path on SCHEDULED reminder returns updated fields")
    void updateHappyPath() {
        when(repository.findByIdForUser(reminderId, userId)).thenReturn(Optional.of(scheduled()));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant newTrigger = Instant.now(FIXED_CLOCK).plusSeconds(7200);
        CustomReminder result = service.update(userId, reminderId, "New note", newTrigger, null, null);

        assertThat(result.getNote()).isEqualTo("New note");
        assertThat(result.getTriggerAtUtc()).isEqualTo(newTrigger);
        verify(repository).update(any());
    }

    // CR-U-031
    @Test
    @DisplayName("CR-U-031: update on FIRED reminder throws CustomReminderNotScheduledException")
    void updateFiredThrows() {
        CustomReminder fired = scheduled().toBuilder().status(CustomReminderStatus.FIRED).build();
        when(repository.findByIdForUser(reminderId, userId)).thenReturn(Optional.of(fired));

        assertThatThrownBy(() -> service.update(userId, reminderId, "New note", null, null, null))
                .isInstanceOf(CustomReminderNotScheduledException.class);
    }

    // CR-U-032
    @Test
    @DisplayName("CR-U-032: update on CANCELLED reminder throws CustomReminderNotScheduledException")
    void updateCancelledThrows() {
        CustomReminder cancelled = scheduled().toBuilder().status(CustomReminderStatus.CANCELLED).build();
        when(repository.findByIdForUser(reminderId, userId)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.update(userId, reminderId, "New note", null, null, null))
                .isInstanceOf(CustomReminderNotScheduledException.class);
    }

    // CR-U-033
    @Test
    @DisplayName("CR-U-033: update with trigger moved to past throws CustomReminderTriggerInPastException")
    void updateTriggerMovedToPastThrows() {
        when(repository.findByIdForUser(reminderId, userId)).thenReturn(Optional.of(scheduled()));
        Instant past = Instant.now(FIXED_CLOCK).minusSeconds(60);

        assertThatThrownBy(() -> service.update(userId, reminderId, null, past, null, null))
                .isInstanceOf(CustomReminderTriggerInPastException.class);

        verify(repository, never()).update(any());
    }

    // CR-U-034
    @Test
    @DisplayName("CR-U-034: update on not-found or non-owner throws CustomReminderNotFoundException")
    void updateNotFoundThrows() {
        when(repository.findByIdForUser(reminderId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(userId, reminderId, "New note", null, null, null))
                .isInstanceOf(CustomReminderNotFoundException.class);
    }

    // CR-U-035
    @Test
    @DisplayName("CR-U-035: update partial update only changes supplied fields")
    void updatePartialUpdatePreservesOtherFields() {
        when(repository.findByIdForUser(reminderId, userId)).thenReturn(Optional.of(scheduled()));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomReminder result = service.update(userId, reminderId, "New", null, null, null);

        assertThat(result.getTitle()).isEqualTo("Old");
        assertThat(result.getNote()).isEqualTo("New");
        assertThat(result.getChannels()).containsExactly(CustomReminderChannel.IN_APP);
        assertThat(result.getStage()).isEqualTo(CustomReminderStage.SCREENING);
    }

    // NS-U-11
    @Test
    @DisplayName("NS-U-11: update ignores any incoming title value, persisted reminder retains its original title")
    void updateIgnoresIncomingTitleAndPreservesOriginal() {
        when(repository.findByIdForUser(reminderId, userId)).thenReturn(Optional.of(scheduled()));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomReminder result = service.update(userId, reminderId, "New note", null, null, null);

        assertThat(result.getTitle()).isEqualTo("Old");
        verify(repository).update(argThat(r -> "Old".equals(r.getTitle())));
    }

    // CR-U-040
    @Test
    @DisplayName("CR-U-040: cancel SCHEDULED reminder marks it CANCELLED, no exception")
    void cancelScheduledMarksCancelled() {
        when(repository.findByIdForUser(reminderId, userId)).thenReturn(Optional.of(scheduled()));

        service.cancel(userId, reminderId);

        verify(repository).markCancelled(reminderId);
    }

    // CR-U-041
    @Test
    @DisplayName("CR-U-041: cancel already-CANCELLED is idempotent, no markCancelled call")
    void cancelAlreadyCancelledIsNoOp() {
        CustomReminder cancelled = scheduled().toBuilder().status(CustomReminderStatus.CANCELLED).build();
        when(repository.findByIdForUser(reminderId, userId)).thenReturn(Optional.of(cancelled));

        service.cancel(userId, reminderId);

        verify(repository, never()).markCancelled(any());
    }

    // CR-U-042
    @Test
    @DisplayName("CR-U-042: cancel FIRED reminder throws CustomReminderNotScheduledException")
    void cancelFiredThrows() {
        CustomReminder fired = scheduled().toBuilder().status(CustomReminderStatus.FIRED).build();
        when(repository.findByIdForUser(reminderId, userId)).thenReturn(Optional.of(fired));

        assertThatThrownBy(() -> service.cancel(userId, reminderId))
                .isInstanceOf(CustomReminderNotScheduledException.class);
    }

    // CR-U-043
    @Test
    @DisplayName("CR-U-043: cancel not-found or non-owner throws CustomReminderNotFoundException")
    void cancelNotFoundThrows() {
        when(repository.findByIdForUser(reminderId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(userId, reminderId))
                .isInstanceOf(CustomReminderNotFoundException.class);
    }

    // CR-U-050
    @Test
    @DisplayName("CR-U-050: get owner retrieves reminder")
    void getOwnerRetrievesReminder() {
        when(repository.findByIdForUser(reminderId, userId)).thenReturn(Optional.of(scheduled()));

        CustomReminder result = service.get(userId, reminderId);

        assertThat(result.getId()).isEqualTo(reminderId);
    }

    // CR-U-051
    @Test
    @DisplayName("CR-U-051: get not-found or non-owner throws CustomReminderNotFoundException")
    void getNotFoundThrows() {
        when(repository.findByIdForUser(reminderId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(userId, reminderId))
                .isInstanceOf(CustomReminderNotFoundException.class);
    }

    // CR-U-060
    @Test
    @DisplayName("CR-U-060: list-mine default returns only SCHEDULED ordered by trigger asc")
    void listMineDefaultReturnsOnlyScheduledAsc() {
        Instant now = Instant.now(FIXED_CLOCK);
        CustomReminder r1 = scheduled().toBuilder().id(UUID.randomUUID()).triggerAtUtc(now.plusSeconds(300)).build();
        CustomReminder r2 = scheduled().toBuilder().id(UUID.randomUUID()).triggerAtUtc(now.plusSeconds(100)).build();
        CustomReminder r3 = scheduled().toBuilder().id(UUID.randomUUID()).triggerAtUtc(now.plusSeconds(200)).build();
        when(repository.findAllForUser(userId, false)).thenReturn(List.of(r1, r2, r3));

        List<CustomReminder> result = service.list(userId, false);

        assertThat(result).extracting(CustomReminder::getId)
                .containsExactly(r2.getId(), r3.getId(), r1.getId());
    }

    // CR-U-061
    @Test
    @DisplayName("CR-U-061: list-mine includeFired=true returns all statuses ordered by trigger desc")
    void listMineIncludeFiredOrdersDesc() {
        Instant now = Instant.now(FIXED_CLOCK);
        CustomReminder r1 = scheduled().toBuilder().id(UUID.randomUUID()).triggerAtUtc(now.plusSeconds(100)).build();
        CustomReminder r2 = scheduled().toBuilder().id(UUID.randomUUID()).triggerAtUtc(now.plusSeconds(300)).build();
        when(repository.findAllForUser(userId, true)).thenReturn(List.of(r1, r2));

        List<CustomReminder> result = service.list(userId, true);

        assertThat(result).extracting(CustomReminder::getId).containsExactly(r2.getId(), r1.getId());
    }

    // CR-U-062
    @Test
    @DisplayName("CR-U-062: list-mine empty result when user has no reminders")
    void listMineEmptyResult() {
        when(repository.findAllForUser(userId, false)).thenReturn(List.of());

        List<CustomReminder> result = service.list(userId, false);

        assertThat(result).isEmpty();
    }

    // CR-U-070
    @Test
    @DisplayName("CR-U-070: list-by-application application not owned still returns from owner-scoped query (no gateway call)")
    void listByApplicationDoesNotCallOwnershipGateway() {
        when(repository.findAllForUserAndApplication(userId, applicationId, false)).thenReturn(List.of());

        service.list(userId, applicationId, false);

        verify(ownershipGateway, never()).isOwnedByUser(any(), any());
    }

    // CR-U-071
    @Test
    @DisplayName("CR-U-071: list-by-application owner gets only their SCHEDULED reminders for that app")
    void listByApplicationReturnsOnlyForThatApp() {
        CustomReminder r1 = scheduled().toBuilder().id(UUID.randomUUID()).build();
        CustomReminder r2 = scheduled().toBuilder().id(UUID.randomUUID()).build();
        when(repository.findAllForUserAndApplication(userId, applicationId, false)).thenReturn(List.of(r1, r2));

        List<CustomReminder> result = service.list(userId, applicationId, false);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.getApplicationId().equals(applicationId));
    }
}
