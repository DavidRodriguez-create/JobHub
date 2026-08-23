package com.davidcreate.jobhub.auth.application.port.in;

public record OAuthAuthorizationResult(String authorizationUrl, String state) {
}
