package com.davidcreate.jobhub.job.domain.exception;

import com.davidcreate.jobhub.job.domain.model.TriggerKind;

public class TriggerInProgressException extends RuntimeException {

    public TriggerInProgressException(TriggerKind kind) {
        super("A " + kind.value() + " pass is already queued or running");
    }
}
