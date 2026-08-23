package com.davidcreate.jobhub.notification.domain.port.out;

import com.davidcreate.jobhub.notification.domain.model.CustomReminder;

public interface CustomReminderMailer {

    void send(String recipientEmail, CustomReminder reminder);
}
