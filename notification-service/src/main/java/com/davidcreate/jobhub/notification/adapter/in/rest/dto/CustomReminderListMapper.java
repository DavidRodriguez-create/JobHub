package com.davidcreate.jobhub.notification.adapter.in.rest.dto;

import com.davidcreate.jobhub.notification.contract.model.CustomReminderList;
import com.davidcreate.jobhub.notification.domain.model.CustomReminder;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class CustomReminderListMapper {

    private final CustomReminderResponseMapper responseMapper;

    public CustomReminderListMapper(CustomReminderResponseMapper responseMapper) {
        this.responseMapper = responseMapper;
    }

    public CustomReminderList toList(List<CustomReminder> reminders) {
        return new CustomReminderList()
                .content(reminders.stream().map(responseMapper::toResponse).toList());
    }
}
