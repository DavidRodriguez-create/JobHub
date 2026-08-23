package com.davidcreate.jobhub.application.domain.exception;

import java.util.UUID;

public class AlreadyTerminalException extends RuntimeException {
    public AlreadyTerminalException(UUID id) {
        super("application is already in a terminal status: " + id);
    }
}
