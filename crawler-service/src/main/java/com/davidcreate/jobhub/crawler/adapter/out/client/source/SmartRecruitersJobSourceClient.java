package com.davidcreate.jobhub.crawler.adapter.out.client.source;

import com.davidcreate.jobhub.crawler.domain.model.JobPost;
import com.davidcreate.jobhub.crawler.domain.model.PullResult;
import com.davidcreate.jobhub.crawler.domain.model.PullTarget;
import com.davidcreate.jobhub.crawler.domain.port.out.JobSourceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static com.davidcreate.jobhub.crawler.adapter.out.client.support.JobFieldDefaults.DEFAULT_LANGUAGES;
import static com.davidcreate.jobhub.crawler.adapter.out.client.support.JobFieldDefaults.normalizeEmploymentType;

@ApplicationScoped
public class SmartRecruitersJobSourceClient extends BaseJobSourceClient implements JobSourceClient {

    private static final Logger LOG = Logger.getLogger(SmartRecruitersJobSourceClient.class);
    private static final String BASE_URL = "https://api.smartrecruiters.com/v1/companies/%s/postings";
    private static final int PAGE_SIZE = 100;

    private final ObjectMapper mapper;
    private final int maxPages;

    @Inject
    public SmartRecruitersJobSourceClient(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "crawler.smartrecruiters.max-pages", defaultValue = "5") int maxPages) {
        this.mapper = objectMapper;
        this.maxPages = maxPages;
    }

    @Override
    public boolean supports(String sourceType) {
        return "smartrecruiters".equals(sourceType);
    }

    @Override
    public PullResult crawl(PullTarget target) {
        String company = null;
        try {
            JsonNode config = mapper.readTree(target.getScraperConfig());
            company = config.path("company").asText(null);
            List<String> countries = extractCountries(config);
            String query = extractQuery(config);

            // SmartRecruiters' postings endpoint only supports a single country value per
            // request (comma-joined or repeated "country" params both return zero results,
            // confirmed live). A multi-country target is swept one request-per-country, each
            // sweep independently paginated up to the page cap, then merged and deduped by
            // contentHash since the same posting can surface identically under more than one
            // country facet.
            Map<String, JobPost> mergedJobs = new LinkedHashMap<>();

            if (countries.isEmpty()) {
                sweepCountry(target, company, null, query, mergedJobs);
            } else {
                for (String country : countries) {
                    sweepCountry(target, company, country, query, mergedJobs);
                }
            }

            LOG.debugf("SmartRecruiters %s: found %d jobs", company, mergedJobs.size());
            return PullResult.success(new ArrayList<>(mergedJobs.values()));

        } catch (SmartRecruitersHttpException e) {
            LOG.warnf("SmartRecruiters %s returned %d", company, e.httpStatus);
            return PullResult.failure(e.getMessage(), e.httpStatus);
        } catch (Exception e) {
            logCrawlFailure(LOG, "Error crawling SmartRecruiters " + company, e);
            return PullResult.failure(e.getMessage());
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void sweepCountry(PullTarget target, String company, String country, String query,
            Map<String, JobPost> mergedJobs) throws Exception {
        int offset = 0;
        int pagesFetched = 0;

        while (pagesFetched < maxPages) {
            HttpRequest request = buildRequest(company, country, query, offset);
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
            pagesFetched++;

            if (response.statusCode() != 200) {
                throw new SmartRecruitersHttpException(response.statusCode(),
                        String.format("HTTP %d from %s", response.statusCode(), response.uri()));
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode content = root.path("content");
            int totalFound = root.path("totalFound").asInt(0);

            if (!content.isArray() || content.isEmpty()) {
                break;
            }

            for (JsonNode item : content) {
                JobPost job = mapJob(target, company, item);
                mergedJobs.putIfAbsent(job.getContentHash(), job);
            }

            offset += content.size();
            if (offset >= totalFound) {
                break;
            }
        }
    }

    private HttpRequest buildRequest(String company, String country, String query, int offset) {
        StringBuilder uri = new StringBuilder(String.format(BASE_URL, company))
                .append("?limit=").append(PAGE_SIZE)
                .append("&offset=").append(offset);

        if (country != null && !country.isBlank()) {
            uri.append("&country=").append(urlEncode(country));
        }
        if (query != null && !query.isBlank()) {
            uri.append("&q=").append(urlEncode(query));
        }

        return HttpRequest.newBuilder()
                .uri(URI.create(uri.toString()))
                .timeout(requestTimeout())
                .GET()
                .build();
    }

    private List<String> extractCountries(JsonNode config) {
        JsonNode countriesNode = config.path("countries");
        if (!countriesNode.isArray() || countriesNode.isEmpty()) {
            return List.of();
        }
        return StreamSupport.stream(countriesNode.spliterator(), false)
                .map(n -> n.asText(""))
                .filter(c -> !c.isBlank())
                .toList();
    }

    private String extractQuery(JsonNode config) {
        String query = config.path("query").asText(null);
        return (query == null || query.isBlank()) ? null : query;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private JobPost mapJob(PullTarget target, String company, JsonNode item) {
        String id = item.path("id").asText("");
        String title = item.path("name").asText("");
        String url = String.format("https://jobs.smartrecruiters.com/%s/%s", company, id);

        JsonNode location = item.path("location");
        String city = location.hasNonNull("city") ? location.path("city").asText() : null;
        String country = location.hasNonNull("country") ? location.path("country").asText() : null;

        String employmentTypeLabel = item.path("typeOfEmployment").path("label").asText(null);
        String employmentType = normalizeEmploymentType(employmentTypeLabel);

        return JobPost.builder()
                .targetId(target.getId())
                .title(title)
                .url(url)
                .city(city)
                .country(country)
                .employmentType(employmentType)
                .languages(DEFAULT_LANGUAGES)
                .description(null)
                .contentHash(JobPost.computeHash(title, target.getCompanyName(), city, null))
                .build();
    }

    // ─── Private exception ───────────────────────────────────────────────────

    private static class SmartRecruitersHttpException extends RuntimeException {
        final int httpStatus;

        SmartRecruitersHttpException(int httpStatus, String message) {
            super(message);
            this.httpStatus = httpStatus;
        }
    }
}
