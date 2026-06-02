package com.davidcreate.jobhub.application.domain.entity;

import com.davidcreate.jobhub.application.domain.valueobject.JobInfo;
import com.davidcreate.jobhub.application.domain.valueobject.TimelineEntry;

import java.util.List;

/**
 * An application paired with its resolved job info — the shape every read/write of the
 * application surface returns, since the API always embeds a job summary.
 *
 * <p>{@code timeline} is populated only for the single-application detail view; list and
 * write responses leave it {@code null} so it is omitted from their payloads.
 */
public record ApplicationView(Application application, JobInfo job, List<TimelineEntry> timeline) {

    public ApplicationView(Application application, JobInfo job) {
        this(application, job, null);
    }
}
