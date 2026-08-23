package com.davidcreate.jobhub.application.application.port.out;

import com.davidcreate.jobhub.application.domain.entity.UpcomingNextStep;

import java.util.List;

public interface UpcomingNextStepRepository {

    /**
     * Returns upcoming next steps across ALL users whose {@code nextStepDate} falls within
     * a forward window of {@code withinHours} from now, restricted to applications with a
     * non-empty {@code nextStepLabel} and a non-terminal {@code status} (ADR 0009).
     * Returns an empty list when nothing is upcoming, never {@code null}.
     */
    List<UpcomingNextStep> findUpcoming(int withinHours);
}
