package com.davidcreate.jobhub.auth.application.port.in;

public record VerifyLoginTwoFactorCommand(String twoFactorToken, String totpCode) {
}
