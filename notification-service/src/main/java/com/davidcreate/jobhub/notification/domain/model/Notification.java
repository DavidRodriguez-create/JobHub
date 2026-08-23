package com.davidcreate.jobhub.notification.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class Notification {

    private final UUID id;
    private final UUID userId;
    private final NotificationType type;
    private final String title;
    private final String message;
    private final boolean read;
    private final LocalDateTime createdAt;
    private final UUID applicationId;

    /**
     * Company name of the tied application, resolved at read time via
     * {@link com.davidcreate.jobhub.notification.domain.port.out.ApplicationSummaryGateway}
     * (ADR 0014). Null when there is no applicationId, or when the application could not
     * be resolved. Not persisted: populated by the service layer when building a page.
     */
    private final String company;

    /**
     * Job post / application title of the tied application, resolved at read time.
     * Null under the same conditions as {@link #company}. Not persisted.
     */
    private final String jobTitle;

    /**
     * Real company logo URL of the tied application, resolved at read time from the
     * application snapshot (ADR 0015). Null when there is no applicationId, when the
     * application is unresolvable, or when the resolved application has no stored logo.
     * Not persisted: populated by the service layer alongside company/jobTitle.
     */
    private final URI companyLogoUrl;
}
