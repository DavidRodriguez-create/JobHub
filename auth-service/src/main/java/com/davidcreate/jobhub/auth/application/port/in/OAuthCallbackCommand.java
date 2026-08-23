package com.davidcreate.jobhub.auth.application.port.in;

/**
 * {@code cookieState} is the value bound by the preceding {@code GET /oauth/{provider}/start}
 * call (read from the HttpOnly cookie by the REST layer); {@code submittedState} is the
 * value the UI relayed from the provider redirect. A mismatch (or a missing cookie) fails
 * CSRF validation before any outbound call or DB write (ADR 0027, BR10).
 */
public record OAuthCallbackCommand(String provider, String code, String submittedState, String cookieState) {
}
