package com.davidcreate.jobhub.auth.application.port.in;

public record LoginCommand(String email, String password) {
}
