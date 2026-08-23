package com.davidcreate.jobhub.notification.domain.service;

import com.davidcreate.jobhub.notification.domain.exception.ApplicationNotOwnedException;
import com.davidcreate.jobhub.notification.domain.exception.CustomReminderNotFoundException;
import com.davidcreate.jobhub.notification.domain.exception.CustomReminderNotScheduledException;
import com.davidcreate.jobhub.notification.domain.model.CustomReminder;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderChannel;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderStage;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderStatus;
import com.davidcreate.jobhub.notification.domain.port.in.CancelCustomReminderUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.CreateCustomReminderUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.GetCustomReminderUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.ListCustomRemindersByApplicationUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.ListMyCustomRemindersUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.UpdateCustomReminderUseCase;
import com.davidcreate.jobhub.notification.domain.port.out.ApplicationOwnershipGateway;
import com.davidcreate.jobhub.notification.domain.port.out.CustomReminderRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class CustomReminderService implements CreateCustomReminderUseCase, UpdateCustomReminderUseCase,
        CancelCustomReminderUseCase, GetCustomReminderUseCase, ListMyCustomRemindersUseCase,
        ListCustomRemindersByApplicationUseCase {

    private final CustomReminderRepository repository;
    private final ApplicationOwnershipGateway ownershipGateway;
    private final Clock clock;
    private final int titleMaxLength;
    private final int noteMaxLength;

    public CustomReminderService(CustomReminderRepository repository,
                                  ApplicationOwnershipGateway ownershipGateway,
                                  Clock clock) {
        this(repository, ownershipGateway, clock,
                CustomReminder.TITLE_MAX_LENGTH_DEFAULT, CustomReminder.NOTE_MAX_LENGTH_DEFAULT);
    }

    @Inject
    public CustomReminderService(CustomReminderRepository repository,
                                  ApplicationOwnershipGateway ownershipGateway,
                                  Clock clock,
                                  @ConfigProperty(name = "notification.custom-reminder.title.max-length", defaultValue = "200") int titleMaxLength,
                                  @ConfigProperty(name = "notification.custom-reminder.note.max-length", defaultValue = "2000") int noteMaxLength) {
        this.repository = repository;
        this.ownershipGateway = ownershipGateway;
        this.clock = clock;
        this.titleMaxLength = titleMaxLength;
        this.noteMaxLength = noteMaxLength;
    }

    @Override
    @Transactional
    public CustomReminder create(UUID userId, UUID applicationId, String title, String note,
                                  Instant triggerAtUtc, List<CustomReminderChannel> channels,
                                  CustomReminderStage stage) {
        CustomReminder.validateTrigger(triggerAtUtc, clock);
        CustomReminder.validateTitle(title, titleMaxLength);
        CustomReminder.validateNote(note, noteMaxLength);
        Set<CustomReminderChannel> normalisedChannels = CustomReminder.normaliseChannels(channels);

        if (!ownershipGateway.isOwnedByUser(applicationId, userId)) {
            throw new ApplicationNotOwnedException(applicationId);
        }

        Instant now = Instant.now(clock);
        CustomReminder toSave = CustomReminder.builder()
                .userId(userId)
                .applicationId(applicationId)
                .title(title)
                .note(note)
                .triggerAtUtc(triggerAtUtc)
                .channels(normalisedChannels)
                .stage(stage)
                .status(CustomReminderStatus.SCHEDULED)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return repository.save(toSave);
    }

    @Override
    @Transactional
    public CustomReminder update(UUID userId, UUID reminderId, String note,
                                  Instant triggerAtUtc, List<CustomReminderChannel> channels,
                                  CustomReminderStage stage) {
        CustomReminder existing = repository.findByIdForUser(reminderId, userId)
                .orElseThrow(() -> new CustomReminderNotFoundException(reminderId));

        if (existing.getStatus() != CustomReminderStatus.SCHEDULED) {
            throw new CustomReminderNotScheduledException(reminderId);
        }

        if (triggerAtUtc != null) {
            CustomReminder.validateTrigger(triggerAtUtc, clock);
        }
        if (note != null) {
            CustomReminder.validateNote(note, noteMaxLength);
        }
        Set<CustomReminderChannel> normalisedChannels = channels != null
                ? CustomReminder.normaliseChannels(channels)
                : existing.getChannels();

        // Title is NOT editable (story #207, req 4): always preserved from create time,
        // regardless of any title value a caller may have supplied.
        CustomReminder merged = CustomReminder.builder()
                .id(existing.getId())
                .userId(existing.getUserId())
                .applicationId(existing.getApplicationId())
                .title(existing.getTitle())
                .note(note != null ? note : existing.getNote())
                .triggerAtUtc(triggerAtUtc != null ? triggerAtUtc : existing.getTriggerAtUtc())
                .channels(normalisedChannels)
                .stage(stage != null ? stage : existing.getStage())
                .status(existing.getStatus())
                .createdAt(existing.getCreatedAt())
                .updatedAt(Instant.now(clock))
                .build();

        return repository.update(merged);
    }

    @Override
    @Transactional
    public void cancel(UUID userId, UUID reminderId) {
        CustomReminder existing = repository.findByIdForUser(reminderId, userId)
                .orElseThrow(() -> new CustomReminderNotFoundException(reminderId));

        if (existing.getStatus() == CustomReminderStatus.CANCELLED) {
            return;
        }
        if (existing.getStatus() == CustomReminderStatus.FIRED) {
            throw new CustomReminderNotScheduledException(reminderId);
        }

        repository.markCancelled(reminderId);
    }

    @Override
    public CustomReminder get(UUID userId, UUID reminderId) {
        return repository.findByIdForUser(reminderId, userId)
                .orElseThrow(() -> new CustomReminderNotFoundException(reminderId));
    }

    @Override
    public List<CustomReminder> list(UUID userId, boolean includeFired) {
        List<CustomReminder> reminders = repository.findAllForUser(userId, includeFired);
        return sortByOrder(reminders, includeFired);
    }

    @Override
    public List<CustomReminder> list(UUID userId, UUID applicationId, boolean includeFired) {
        List<CustomReminder> reminders = repository.findAllForUserAndApplication(userId, applicationId, includeFired);
        return sortByOrder(reminders, includeFired);
    }

    private List<CustomReminder> sortByOrder(List<CustomReminder> reminders, boolean includeFired) {
        Comparator<CustomReminder> comparator = Comparator.comparing(CustomReminder::getTriggerAtUtc);
        if (includeFired) {
            comparator = comparator.reversed();
        }
        return reminders.stream().sorted(comparator).toList();
    }
}
