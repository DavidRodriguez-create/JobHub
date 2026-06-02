package com.davidcreate.jobhub.crawler.adapter.out.client;

import com.davidcreate.jobhub.crawler.domain.model.JobPost;
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
import java.util.Map;

import static com.davidcreate.jobhub.crawler.adapter.out.client.JobFieldDefaults.DEFAULT_LANGUAGES;

@ApplicationScoped
public class WorkdayJobSourceClient extends BaseJobSourceClient implements JobSourceClient {

    private static final Logger LOG = Logger.getLogger(WorkdayJobSourceClient.class);

    @Override
    public boolean supports(String sourceType) {
        return "workday".equals(sourceType);
    }

    @Override
    public PullResult crawl(PullTarget target) {
        try {
            JsonNode config = objectMapper.readTree(target.getScraperConfig());
            String apiUrl = config.path("url").asText();
            String searchText = config.path("query").asText("software engineer");
            JsonNode filtersNode = config.path("filters");

            Map<String, Object> facets = filtersNode.isMissingNode()
                    ? Map.of()
                    : objectMapper.convertValue(filtersNode, Map.class);

            Map<String, Object> body = Map.of(
                    "appliedFacets", facets,
                    "limit", 20,
                    "offset", 0,
                    "searchText", searchText);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(requestTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.warnf("Workday %s returned %d", target.getCompanyName(), response.statusCode());
                return PullResult.failure(
                        String.format("HTTP %d from %s", response.statusCode(), response.uri()),
                        response.statusCode());
            }

            List<JobPost> jobs = parseJobs(target, response.body(), apiUrl);
            LOG.infof("Workday %s: found %d jobs", target.getCompanyName(), jobs.size());
            return PullResult.success(jobs);

        } catch (Exception e) {
            LOG.errorf(e, "Error crawling Workday %s", target.getCompanyName());
            return PullResult.failure(e.getMessage());
        }
    }

    private List<JobPost> parseJobs(PullTarget target, String body, String apiUrl) throws Exception {
        List<JobPost> jobs = new ArrayList<>();
        JsonNode root = objectMapper.readTree(body);
        JsonNode postings = root.path("jobPostings");

        if (!postings.isArray())
            return jobs;

        String baseUrl = extractBaseUrl(apiUrl);
        for (JsonNode job : postings) {
            String title = job.path("title").asText("");
            String location = job.path("locationsText").asText("");
            String url = baseUrl + job.path("externalPath").asText("");
            String description = job.path("jobDescription").asText("");

            jobs.add(JobPost.builder()
                    .targetId(target.getId())
                    .title(title)
                    .url(url)
                    .city(location)
                    .description(description)
                    .languages(DEFAULT_LANGUAGES)
                    .contentHash(JobPost.computeHash(title, target.getCompanyName(), location, description))
                    .build());
        }
        return jobs;
    }

    private String extractBaseUrl(String apiUrl) {
        try {
            URI uri = URI.create(apiUrl);
            return uri.getScheme() + "://" + uri.getHost();
        } catch (Exception e) {
            return "";
        }
    }
}