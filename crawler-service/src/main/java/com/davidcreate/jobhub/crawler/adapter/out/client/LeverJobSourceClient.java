// adapter/out/client/LeverJobSourceClient.java
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
            LOG.infof("Lever %s: found %d jobs", slug, jobs.size());
            return PullResult.success(jobs);

        } catch (Exception e) {
            LOG.errorf(e, "Error crawling Lever %s", slug);
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

            jobs.add(JobPost.builder()
                    .targetId(target.getId())
                    .title(title)
                    .url(url)
                    .city(location)
                    .description(description)
                    .employmentType(normalizeEmploymentType(commitment))
                    .languages(DEFAULT_LANGUAGES)
                    .contentHash(JobPost.computeHash(title, target.getCompanyName(), location, description))
                    .build());
        }
        return jobs;
    }
}