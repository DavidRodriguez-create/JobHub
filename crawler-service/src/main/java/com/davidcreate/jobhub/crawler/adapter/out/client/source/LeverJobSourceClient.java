package com.davidcreate.jobhub.crawler.adapter.out.client.source;

import com.davidcreate.jobhub.crawler.adapter.out.client.support.LocationParser;
import com.davidcreate.jobhub.crawler.adapter.out.client.support.SalaryParser;
import com.davidcreate.jobhub.crawler.domain.model.JobPost;
import com.davidcreate.jobhub.crawler.domain.model.JobPostLocation;
import com.davidcreate.jobhub.crawler.domain.model.PullResult;
import com.davidcreate.jobhub.crawler.domain.model.PullTarget;
import com.davidcreate.jobhub.crawler.domain.port.out.JobSourceClient;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static com.davidcreate.jobhub.crawler.adapter.out.client.support.JobFieldDefaults.DEFAULT_LANGUAGES;
import static com.davidcreate.jobhub.crawler.adapter.out.client.support.JobFieldDefaults.normalizeEmploymentType;

@ApplicationScoped
public class LeverJobSourceClient extends BaseJobSourceClient implements JobSourceClient {

    private static final Logger LOG = Logger.getLogger(LeverJobSourceClient.class);
    private static final String API_URL = "https://api.lever.co/v0/postings/%s?mode=json";

    @Override
    public boolean supports(String sourceType) {
        return "lever".equals(sourceType);
    }

    @Override
    public PullResult crawl(PullTarget target) {
        String slug = target.getToken();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(API_URL, slug)))
                    .timeout(requestTimeout())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.warnf("Lever %s returned %d", slug, response.statusCode());
                return PullResult.failure(
                        String.format("HTTP %d from %s", response.statusCode(), response.uri()),
                        response.statusCode());
            }

            List<JobPost> jobs = parseJobs(target, response.body());
            LOG.debugf("Lever %s: found %d jobs", slug, jobs.size());
            return PullResult.success(jobs);

        } catch (Exception e) {
            logCrawlFailure(LOG, "Error crawling Lever " + slug, e);
            return PullResult.failure(e.getMessage());
        }
    }

    private List<JobPost> parseJobs(PullTarget target, String body) throws Exception {
        List<JobPost> jobs = new ArrayList<>();
        JsonNode root = objectMapper.readTree(body);

        if (!root.isArray())
            return jobs;

        for (JsonNode job : root) {
            String title = job.path("text").asText("");
            String url = job.path("hostedUrl").asText("");
            JsonNode categories = job.path("categories");
            String location = categories.path("location").asText("");
            String commitment = categories.path("commitment").asText(null);
            String description = job.path("descriptionPlain").asText("");

            var salary = salaryParser.parseToEur(description);

            String city = LocationParser.parseCity(location);
            String country = LocationParser.parseCountry(location);

            jobs.add(JobPost.builder()
                    .targetId(target.getId())
                    .title(title)
                    .url(url)
                    .city(city)
                    .country(country)
                    .description(description)
                    .compensationMin(salary.map(SalaryParser.EurSalary::min).orElse(null))
                    .compensationMax(salary.map(SalaryParser.EurSalary::max).orElse(null))
                    .employmentType(normalizeEmploymentType(commitment))
                    .languages(DEFAULT_LANGUAGES)
                    .additionalLocations(parseAdditionalLocations(categories, country, city))
                    // ADR 0017 (story #319): content_hash stays keyed on the RAW, unsplit
                    // `categories.location` string, never the split city/country. Splitting
                    // for storage must not change identity/re-crawl matching (BR-319-2).
                    .contentHash(JobPost.computeHash(title, target.getCompanyName(), location, description))
                    .build());
        }
        return jobs;
    }

    /**
     * Every distinct opening in {@code categories.allLocations[]}, comma-split through the
     * same {@link LocationParser} used for the primary, excluding whatever is a
     * case-insensitive match of the primary opening (BR-319-3: the canonical location is not
     * double-stored as a non-primary duplicate of itself). Remaining internal duplicates within
     * the list, or duplicates against a stale existing row, are the persistence-layer's
     * responsibility ({@code JobPostMapper.toLocationEntities}), not re-derived here.
     */
    private List<JobPostLocation> parseAdditionalLocations(JsonNode categories, String primaryCountry, String primaryCity) {
        JsonNode allLocations = categories.path("allLocations");
        if (!allLocations.isArray()) {
            return List.of();
        }

        List<JobPostLocation> additional = new ArrayList<>();
        for (JsonNode entry : allLocations) {
            String raw = entry.asText("");
            if (raw.isBlank()) {
                continue;
            }
            String city = LocationParser.parseCity(raw);
            String country = LocationParser.parseCountry(raw);
            if (isSamePlace(country, city, primaryCountry, primaryCity)) {
                continue;
            }
            additional.add(JobPostLocation.builder()
                    .country(country)
                    .city(city)
                    .primary(false)
                    .build());
        }
        return additional;
    }

    private boolean isSamePlace(String country, String city, String otherCountry, String otherCity) {
        return normalize(country).equals(normalize(otherCountry)) && normalize(city).equals(normalize(otherCity));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
