package com.davidcreate.jobhub.notification.domain.service;

import com.davidcreate.jobhub.notification.domain.model.DigestJob;
import com.davidcreate.jobhub.notification.domain.model.DigestRun;
import com.davidcreate.jobhub.notification.domain.model.DigestRunStatus;
import com.davidcreate.jobhub.notification.domain.model.InterestProfile;
import com.davidcreate.jobhub.notification.domain.model.JobSearchQuery;
import com.davidcreate.jobhub.notification.domain.port.in.SendWeeklyDigestUseCase;
import com.davidcreate.jobhub.notification.domain.port.out.DigestMailer;
import com.davidcreate.jobhub.notification.domain.port.out.DigestRunRepository;
import com.davidcreate.jobhub.notification.domain.port.out.InterestProfileGateway;
import com.davidcreate.jobhub.notification.domain.port.out.JobSearchGateway;
import com.davidcreate.jobhub.notification.domain.port.out.NotificationPreferencesRepository;
import com.davidcreate.jobhub.notification.domain.port.out.UserEmailGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class WeeklyDigestService implements SendWeeklyDigestUseCase {

    private static final Logger LOG = Logger.getLogger(WeeklyDigestService.class);

    private static final String POSTED_WITHIN = "week";
    private static final String SORT = "newest";
    private static final int MAX_KEYWORDS = 3;

    private final NotificationPreferencesRepository preferencesRepository;
    private final DigestRunRepository digestRunRepository;
    private final InterestProfileGateway interestProfileGateway;
    private final UserEmailGateway userEmailGateway;
    private final JobSearchGateway jobSearchGateway;
    private final DigestMailer digestMailer;
    private final int maxJobs;

    public WeeklyDigestService(NotificationPreferencesRepository preferencesRepository,
                                DigestRunRepository digestRunRepository,
                                InterestProfileGateway interestProfileGateway,
                                UserEmailGateway userEmailGateway,
                                JobSearchGateway jobSearchGateway,
                                DigestMailer digestMailer) {
        this(preferencesRepository, digestRunRepository, interestProfileGateway, userEmailGateway,
                jobSearchGateway, digestMailer, 10);
    }

    @Inject
    public WeeklyDigestService(NotificationPreferencesRepository preferencesRepository,
                                DigestRunRepository digestRunRepository,
                                InterestProfileGateway interestProfileGateway,
                                UserEmailGateway userEmailGateway,
                                JobSearchGateway jobSearchGateway,
                                DigestMailer digestMailer,
                                @ConfigProperty(name = "notification.digest.max-jobs", defaultValue = "10") int maxJobs) {
        this.preferencesRepository = preferencesRepository;
        this.digestRunRepository = digestRunRepository;
        this.interestProfileGateway = interestProfileGateway;
        this.userEmailGateway = userEmailGateway;
        this.jobSearchGateway = jobSearchGateway;
        this.digestMailer = digestMailer;
        this.maxJobs = maxJobs;
    }

    @Override
    public void run() {
        List<UUID> candidates = preferencesRepository.findWeeklyDigestCandidateUserIds();
        if (candidates.isEmpty()) {
            LOG.info("Weekly digest run: no opted-in candidates, nothing to do");
            return;
        }

        // BR-6: filter out users already sent this ISO week before doing any outbound work.
        List<UUID> eligible = candidates.stream()
                .filter(userId -> !digestRunRepository.hasSentThisWeek(userId))
                .toList();
        if (eligible.isEmpty()) {
            LOG.infof("Weekly digest run: all %d candidates already sent this week, nothing to do",
                    candidates.size());
            return;
        }

        Map<UUID, String> emails;
        try {
            emails = userEmailGateway.fetchEmails(new HashSet<>(eligible));
        } catch (RuntimeException e) {
            // BR-9: a shared-dependency failure is one systemic log entry, not N per-user traces.
            LOG.errorf(e, "Weekly digest run failed: auth-service unreachable while resolving emails for %d users",
                    eligible.size());
            for (UUID userId : eligible) {
                saveFailed(userId, "auth-service unreachable: " + e.getMessage());
            }
            return;
        }

        for (UUID userId : eligible) {
            processUser(userId, emails.get(userId));
        }
    }

    private void processUser(UUID userId, String email) {
        // BR-8: missing from the email batch (unverified/deleted) is non-eligible, not a failure.
        if (email == null) {
            LOG.debugf("Weekly digest: user %s has no resolvable email (unverified or deleted), skipping", userId);
            return;
        }

        InterestProfile profile;
        try {
            profile = interestProfileGateway.fetch(userId);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Weekly digest: failed to fetch interest profile for user %s", userId);
            saveFailed(userId, "interest-profile fetch failed: " + e.getMessage());
            return;
        }

        boolean personalised = !profile.isEmpty();
        JobSearchQuery query = buildQuery(profile);

        List<DigestJob> matchingJobs;
        try {
            matchingJobs = jobSearchGateway.search(query);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Weekly digest: failed to search jobs for user %s", userId);
            saveFailed(userId, "job search failed: " + e.getMessage());
            return;
        }

        if (matchingJobs.isEmpty()) {
            // BR-4a: zero matches -> skip, don't send an empty email, don't count as "sent".
            LOG.infof("Weekly digest: 0 matching jobs for user %s, skipping (not sent, not failed)", userId);
            digestRunRepository.save(DigestRun.builder()
                    .userId(userId)
                    .sentAt(Instant.now())
                    .jobCount(0)
                    .status(DigestRunStatus.SKIPPED)
                    .build());
            return;
        }

        List<DigestJob> jobsForEmail = matchingJobs.size() > maxJobs
                ? matchingJobs.subList(0, maxJobs)
                : matchingJobs;

        try {
            digestMailer.send(email, jobsForEmail, personalised);
        } catch (RuntimeException e) {
            LOG.errorf(e, "Weekly digest: failed to send digest email to user %s", userId);
            saveFailed(userId, "email send failed: " + e.getMessage());
            return;
        }

        digestRunRepository.save(DigestRun.builder()
                .userId(userId)
                .sentAt(Instant.now())
                .jobCount(jobsForEmail.size())
                .status(DigestRunStatus.SENT)
                .build());
    }

    private JobSearchQuery buildQuery(InterestProfile profile) {
        JobSearchQuery.JobSearchQueryBuilder builder = JobSearchQuery.builder()
                .postedWithin(POSTED_WITHIN)
                .sort(SORT)
                .size(maxJobs);

        if (profile.isEmpty()) {
            return builder.build();
        }

        List<String> keywords = profile.getKeywords();
        if (keywords != null && !keywords.isEmpty()) {
            builder.keyword(String.join(" ", keywords.subList(0, Math.min(MAX_KEYWORDS, keywords.size()))));
        }

        List<String> locations = profile.getLocations();
        if (locations != null && !locations.isEmpty()) {
            builder.locations(locations);
        }

        return builder.build();
    }

    private void saveFailed(UUID userId, String errorMessage) {
        digestRunRepository.save(DigestRun.builder()
                .userId(userId)
                .sentAt(Instant.now())
                .jobCount(0)
                .status(DigestRunStatus.FAILED)
                .errorMessage(errorMessage)
                .build());
    }
}
