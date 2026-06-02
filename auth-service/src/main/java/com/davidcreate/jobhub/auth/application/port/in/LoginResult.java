package com.davidcreate.jobhub.auth.application.port.in;

import com.davidcreate.jobhub.auth.domain.entity.User;

public record LoginResult(String token, long expiresInSeconds, User user) {
}
