package com.davidcreate.jobhub.job.domain.exception;

import com.davidcreate.jobhub.job.domain.model.TriggerKind;

public class NoActiveTriggerException extends RuntimeException {

    public NoActiveTriggerException(TriggerKind kind) {
        super("No active (queued or running) " + kind.value() + " trigger request exists");
    }
}
