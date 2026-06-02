package com.davidcreate.jobhub.auth.application.port.in;

public record ChangePasswordCommand(String currentPassword, String newPassword) {
}
