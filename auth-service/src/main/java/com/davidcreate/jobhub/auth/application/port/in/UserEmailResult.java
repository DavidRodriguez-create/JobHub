package com.davidcreate.jobhub.auth.application.port.in;

import java.util.UUID;

public record UserEmailResult(UUID userId, String email) {
}
