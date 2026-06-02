package com.davidcreate.jobhub.application.domain.entity;

import com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus;
import com.davidcreate.jobhub.application.domain.valueobject.NextDeadline;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class ApplicationStats {

    private final long total;
    private final Map<ApplicationStatus, Long> byStatus;

    // Pipeline
    private final int activeCount;

    // Monthly activity
    private final int monthlyNew;

    // Rates & timings
    private final double responseRate;
    private final double avgReplyDays;
    private final double passThrough;

    // Upcoming (nullable — no active application with an upcoming next-step date)
    private final NextDeadline nextDeadline;
}
