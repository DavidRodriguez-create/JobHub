package com.davidcreate.jobhub.notification.adapter.in.rest;

import com.davidcreate.jobhub.notification.adapter.in.rest.dto.CustomReminderListMapper;
import com.davidcreate.jobhub.notification.adapter.in.rest.dto.CustomReminderResponseMapper;
import com.davidcreate.jobhub.notification.adapter.in.rest.dto.NotificationPageDto;
import com.davidcreate.jobhub.notification.adapter.in.rest.dto.NotificationPreferencesResponseMapper;
import com.davidcreate.jobhub.notification.adapter.in.rest.dto.UnreadCountResponseDto;
import com.davidcreate.jobhub.notification.contract.api.NotificationsApi;
import com.davidcreate.jobhub.notification.contract.model.CreateCustomReminderRequest;
import com.davidcreate.jobhub.notification.contract.model.UpdateCustomReminderRequest;
import com.davidcreate.jobhub.notification.contract.model.UpdateNotificationPreferencesRequest;
import com.davidcreate.jobhub.notification.domain.model.CustomReminder;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderChannel;
import com.davidcreate.jobhub.notification.domain.model.CustomReminderStage;
import com.davidcreate.jobhub.notification.domain.model.NotificationPreferences;
import com.davidcreate.jobhub.notification.domain.model.ReadStatusFilter;
import com.davidcreate.jobhub.notification.domain.port.in.CancelCustomReminderUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.CreateCustomReminderUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.DeleteNotificationUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.GetCustomReminderUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.GetPreferencesUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.GetUnreadCountUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.ListCustomRemindersByApplicationUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.ListMyCustomRemindersUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.ListNotificationsUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.MarkAllNotificationsReadUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.MarkNotificationReadUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.UpdateCustomReminderUseCase;
import com.davidcreate.jobhub.notification.domain.port.in.UpdatePreferencesUseCase;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Path("/notifications")
@ApplicationScoped
@RolesAllowed("user")
public class NotificationResource implements NotificationsApi {

    private final GetPreferencesUseCase getPreferencesUseCase;
    private final UpdatePreferencesUseCase updatePreferencesUseCase;
    private final ListNotificationsUseCase listNotificationsUseCase;
    private final GetUnreadCountUseCase getUnreadCountUseCase;
    private final MarkNotificationReadUseCase markNotificationReadUseCase;
    private final MarkAllNotificationsReadUseCase markAllNotificationsReadUseCase;
    private final DeleteNotificationUseCase deleteNotificationUseCase;
    private final NotificationPreferencesResponseMapper preferencesResponseMapper;
    private final CreateCustomReminderUseCase createCustomReminderUseCase;
    private final UpdateCustomReminderUseCase updateCustomReminderUseCase;
    private final CancelCustomReminderUseCase cancelCustomReminderUseCase;
    private final GetCustomReminderUseCase getCustomReminderUseCase;
    private final ListMyCustomRemindersUseCase listMyCustomRemindersUseCase;
    private final ListCustomRemindersByApplicationUseCase listCustomRemindersByApplicationUseCase;
    private final CustomReminderResponseMapper customReminderResponseMapper;
    private final CustomReminderListMapper customReminderListMapper;
    private final JsonWebToken jwt;

    @ConfigProperty(name = "notification.list.max-size", defaultValue = "100")
    int maxSize;

    public NotificationResource(GetPreferencesUseCase getPreferencesUseCase,
                                 UpdatePreferencesUseCase updatePreferencesUseCase,
                                 ListNotificationsUseCase listNotificationsUseCase,
                                 GetUnreadCountUseCase getUnreadCountUseCase,
                                 MarkNotificationReadUseCase markNotificationReadUseCase,
                                 MarkAllNotificationsReadUseCase markAllNotificationsReadUseCase,
                                 DeleteNotificationUseCase deleteNotificationUseCase,
                                 NotificationPreferencesResponseMapper preferencesResponseMapper,
                                 CreateCustomReminderUseCase createCustomReminderUseCase,
                                 UpdateCustomReminderUseCase updateCustomReminderUseCase,
                                 CancelCustomReminderUseCase cancelCustomReminderUseCase,
                                 GetCustomReminderUseCase getCustomReminderUseCase,
                                 ListMyCustomRemindersUseCase listMyCustomRemindersUseCase,
                                 ListCustomRemindersByApplicationUseCase listCustomRemindersByApplicationUseCase,
                                 CustomReminderResponseMapper customReminderResponseMapper,
                                 CustomReminderListMapper customReminderListMapper,
                                 JsonWebToken jwt) {
        this.getPreferencesUseCase = getPreferencesUseCase;
        this.updatePreferencesUseCase = updatePreferencesUseCase;
        this.listNotificationsUseCase = listNotificationsUseCase;
        this.getUnreadCountUseCase = getUnreadCountUseCase;
        this.markNotificationReadUseCase = markNotificationReadUseCase;
        this.markAllNotificationsReadUseCase = markAllNotificationsReadUseCase;
        this.deleteNotificationUseCase = deleteNotificationUseCase;
        this.preferencesResponseMapper = preferencesResponseMapper;
        this.createCustomReminderUseCase = createCustomReminderUseCase;
        this.updateCustomReminderUseCase = updateCustomReminderUseCase;
        this.cancelCustomReminderUseCase = cancelCustomReminderUseCase;
        this.getCustomReminderUseCase = getCustomReminderUseCase;
        this.listMyCustomRemindersUseCase = listMyCustomRemindersUseCase;
        this.listCustomRemindersByApplicationUseCase = listCustomRemindersByApplicationUseCase;
        this.customReminderResponseMapper = customReminderResponseMapper;
        this.customReminderListMapper = customReminderListMapper;
        this.jwt = jwt;
    }

    // ── Preferences (Story #78) ───────────────────────────────────────────────

    @Override
    public Response getNotificationPreferences() {
        NotificationPreferences prefs = getPreferencesUseCase.getPreferences(userId());
        return Response.ok(preferencesResponseMapper.toResponse(prefs)).build();
    }

    @Override
    public Response updateNotificationPreferences(UpdateNotificationPreferencesRequest request) {
        NotificationPreferences prefs = updatePreferencesUseCase.updatePreferences(
                userId(),
                request.getWeeklyDigestEmail(),
                request.getInAppNotificationsEnabled(),
                request.getInterviewReminders(),
                request.getInterviewReminderEmail(),
                request.getGhostedAlert()
        );
        return Response.ok(preferencesResponseMapper.toResponse(prefs)).build();
    }

    // ── Notification center (Story #79) ───────────────────────────────────────

    @Override
    public Response listNotifications(Integer page, Integer size, String readStatus) {
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;
        ReadStatusFilter filter = parseReadStatus(readStatus);
        validatePagination(p, s);

        com.davidcreate.jobhub.notification.domain.model.NotificationPage result =
                listNotificationsUseCase.listNotifications(userId(), p, s, filter);

        return Response.ok(NotificationPageDto.from(result))
                .header("X-Total-Count", String.valueOf(result.getTotalElements()))
                .build();
    }

    @Override
    public Response getUnreadCount() {
        long count = getUnreadCountUseCase.getUnreadCount(userId());
        return Response.ok(new UnreadCountResponseDto(count)).build();
    }

    @Override
    public Response markNotificationRead(UUID id) {
        markNotificationReadUseCase.markNotificationRead(userId(), id);
        return Response.noContent().build();
    }

    @Override
    public Response markAllNotificationsRead() {
        markAllNotificationsReadUseCase.markAllNotificationsRead(userId());
        return Response.noContent().build();
    }

    @Override
    public Response deleteNotification(UUID id) {
        deleteNotificationUseCase.delete(id, userId());
        return Response.noContent().build();
    }

    // ── Custom reminders (Story #134) ───────────────────────────────────────────

    @Override
    public Response createCustomReminder(CreateCustomReminderRequest request) {
        CustomReminder reminder = createCustomReminderUseCase.create(
                userId(),
                request.getApplicationId(),
                request.getTitle(),
                request.getNote(),
                request.getTriggerAtUtc().toInstant(),
                toDomainChannels(request.getChannels()),
                toDomainStage(request.getStage())
        );

        return Response.created(UriBuilder.fromPath("/notifications/custom-reminders/{id}").build(reminder.getId()))
                .entity(customReminderResponseMapper.toResponse(reminder))
                .build();
    }

    @Override
    public Response getCustomReminder(UUID id) {
        CustomReminder reminder = getCustomReminderUseCase.get(userId(), id);
        return Response.ok(customReminderResponseMapper.toResponse(reminder)).build();
    }

    @Override
    public Response updateCustomReminder(UUID id, UpdateCustomReminderRequest request) {
        Instant triggerAtUtc = request.getTriggerAtUtc() != null ? request.getTriggerAtUtc().toInstant() : null;
        List<CustomReminderChannel> channels = request.getChannels() != null && !request.getChannels().isEmpty()
                ? toDomainChannels(request.getChannels())
                : null;

        CustomReminder reminder = updateCustomReminderUseCase.update(
                userId(), id, request.getNote(), triggerAtUtc, channels,
                toDomainStage(request.getStage())
        );

        return Response.ok(customReminderResponseMapper.toResponse(reminder)).build();
    }

    @Override
    public Response deleteCustomReminder(UUID id) {
        cancelCustomReminderUseCase.cancel(userId(), id);
        return Response.noContent().build();
    }

    @Override
    public Response listMyCustomReminders(UUID applicationId, Boolean includeFired) {
        boolean flag = includeFired != null && includeFired;
        List<CustomReminder> reminders = applicationId == null
                ? listMyCustomRemindersUseCase.list(userId(), flag)
                : listCustomRemindersByApplicationUseCase.list(userId(), applicationId, flag);
        return Response.ok(customReminderListMapper.toList(reminders)).build();
    }

    private List<CustomReminderChannel> toDomainChannels(List<com.davidcreate.jobhub.notification.contract.model.CustomReminderChannel> channels) {
        if (channels == null) {
            return List.of();
        }
        return channels.stream().map(c -> CustomReminderChannel.valueOf(c.name())).toList();
    }

    private CustomReminderStage toDomainStage(com.davidcreate.jobhub.notification.contract.model.CustomReminderStage stage) {
        return stage != null ? CustomReminderStage.valueOf(stage.name()) : null;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be >= 0");
        }
        if (size < 1) {
            throw new BadRequestException("size must be >= 1");
        }
        if (size > maxSize) {
            throw new BadRequestException("size must be <= " + maxSize);
        }
    }

    private static ReadStatusFilter parseReadStatus(String raw) {
        if (raw == null) {
            return ReadStatusFilter.ALL;
        }
        try {
            return ReadStatusFilter.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("readStatus must be one of: all, read, unread");
        }
    }

    private UUID userId() {
        return UUID.fromString(jwt.getSubject());
    }
}
