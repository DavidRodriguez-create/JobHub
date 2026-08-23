package com.davidcreate.jobhub.auth.application.port.in;

public record TwoFactorSetupResult(String otpauthUri, String setupKey) {
}
