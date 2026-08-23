package com.davidcreate.jobhub.application.application.port.in;

import com.davidcreate.jobhub.application.domain.entity.UpcomingNextStep;

import java.util.List;

public interface GetUpcomingNextStepsUseCase {

    /**
     * Returns upcoming next steps across ALL users within {@code withinHours} of now.
     * Returns an empty list when nothing is upcoming, never {@code null}.
     */
    List<UpcomingNextStep> handle(int withinHours);
}
