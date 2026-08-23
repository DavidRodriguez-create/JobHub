package com.davidcreate.jobhub.application.adapter.out.persistence;

import com.davidcreate.jobhub.application.adapter.out.persistence.support.KeywordExtractor;
import com.davidcreate.jobhub.application.application.port.out.InterestProfileRepository;
import com.davidcreate.jobhub.application.domain.entity.InterestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aggregates a user's application history (crawled-job snapshots and manual entries) into
 * an {@link InterestProfile} for the internal {@code /internal/users/{userId}/interest-profile}
 * endpoint (ADR 0008). Each result list is capped at 5 entries, ordered by frequency.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class InterestProfilePanacheRepository implements InterestProfileRepository {

    private static final int MAX_ENTRIES = 5;

    private static final String JOB_INFO_QUERY = """
            SELECT s.title AS title, s.company AS company, s.location AS location
            FROM applications.application a
            JOIN applications.job_post_snapshot s ON s.id = a.job_post_snapshot_id
            WHERE a.user_id = :userId
            UNION ALL
            SELECT u.title AS title, u.company AS company, u.location AS location
            FROM applications.application a
            JOIN applications.user_job_post u ON u.id = a.user_job_post_id
            WHERE a.user_id = :userId
            """;

    private final EntityManager entityManager;

    @Override
    public InterestProfile findInterestProfile(UUID userId) {
        @SuppressWarnings("unchecked")
        List<Tuple> rows = entityManager.createNativeQuery(JOB_INFO_QUERY, Tuple.class)
                .setParameter("userId", userId)
                .getResultList();

        List<String> titles = new ArrayList<>();
        Map<String, Integer> locationCounts = new LinkedHashMap<>();
        Map<String, Integer> companyCounts = new LinkedHashMap<>();

        for (Tuple row : rows) {
            String title = (String) row.get("title");
            String company = (String) row.get("company");
            String location = (String) row.get("location");

            if (title != null && !title.isBlank()) {
                titles.add(title);
            }
            if (company != null && !company.isBlank()) {
                companyCounts.merge(company, 1, Integer::sum);
            }
            if (location != null && !location.isBlank()) {
                locationCounts.merge(location, 1, Integer::sum);
            }
        }

        return InterestProfile.builder()
                .userId(userId)
                .locations(topByFrequency(locationCounts))
                .companies(topByFrequency(companyCounts))
                .keywords(KeywordExtractor.topKeywords(titles, MAX_ENTRIES))
                .build();
    }

    private List<String> topByFrequency(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(MAX_ENTRIES)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
