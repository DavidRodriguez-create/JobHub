package com.davidcreate.jobhub.auth.application.port.in;

public record ChangePasswordCommand(String currentPassword, String newPassword, String totpCode) {

    public ChangePasswordCommand(String currentPassword, String newPassword) {
        this(currentPassword, newPassword, null);
    }
}
