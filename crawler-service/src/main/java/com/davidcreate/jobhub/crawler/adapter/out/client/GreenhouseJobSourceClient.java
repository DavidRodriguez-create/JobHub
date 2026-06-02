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

import static com.davidcreate.jobhub.crawler.adapter.out.client.JobFieldDefaults.DEFAULT_LANGUAGES;
import static com.davidcreate.jobhub.crawler.adapter.out.client.JobFieldDefaults.normalizeEmploymentType;

@ApplicationScoped
public class GreenhouseJobSourceClient extends BaseJobSourceClient implements JobSourceClient {

    private static final Logger LOG = Logger.getLogger(GreenhouseJobSourceClient.class);
    private static final String API_URL = "https://boards-api.greenhouse.io/v1/boards/%s/jobs?content=true";

    @Override
    public boolean supports(String sourceType) {
        return "greenhouse".equals(sourceType);
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
                LOG.warnf("Greenhouse %s returned %d", slug, response.statusCode());
                return PullResult.failure(
                        String.format("HTTP %d from %s", response.statusCode(), response.uri()),
                        response.statusCode());
            }

            List<JobPost> jobs = parseJobs(target, response.body());
            LOG.infof("Greenhouse %s: found %d jobs", slug, jobs.size());
            return PullResult.success(jobs);

        } catch (Exception e) {
            LOG.errorf(e, "Error crawling Greenhouse %s", slug);
            return PullResult.failure(e.getMessage());
        }
    }

    private List<JobPost> parseJobs(PullTarget target, String body) throws Exception {
        List<JobPost> jobs = new ArrayList<>();
        JsonNode root = objectMapper.readTree(body);
        JsonNode jobsNode = root.get("jobs");

        if (jobsNode == null || !jobsNode.isArray())
            return jobs;

        for (JsonNode job : jobsNode) {
            String title = job.path("title").asText("");
            String url = job.path("absolute_url").asText("");
            String location = job.path("location").path("name").asText("");
            String description = job.path("content").asText("");

            jobs.add(JobPost.builder()
                    .targetId(target.getId())
                    .title(title)
                    .url(url)
                    .city(parseCity(location))
                    .country(parseCountry(location))
                    .description(description)
                    .employmentType(parseEmploymentType(job))
                    .languages(DEFAULT_LANGUAGES)
                    .contentHash(JobPost.computeHash(title, target.getCompanyName(), location, description))
                    .build());
        }
        return jobs;
    }

    private String parseEmploymentType(JsonNode job) {
        JsonNode metadata = job.path("metadata");
        if (!metadata.isArray()) {
            return null;
        }
        for (JsonNode entry : metadata) {
            String name = entry.path("name").asText("");
            if ("type".equalsIgnoreCase(name) || "employment type".equalsIgnoreCase(name)) {
                return normalizeEmploymentType(entry.path("value").asText(null));
            }
        }
        return null;
    }

    private String parseCity(String location) {
        if (location == null || location.isBlank())
            return null;
        return location.split(",")[0].trim();
    }

    private String parseCountry(String location) {
        if (location == null || location.isBlank())
            return null;
        String[] parts = location.split(",");
        return parts.length > 1 ? parts[parts.length - 1].trim() : null;
    }
}