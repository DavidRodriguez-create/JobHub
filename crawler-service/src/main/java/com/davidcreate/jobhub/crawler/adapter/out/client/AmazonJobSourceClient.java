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

@ApplicationScoped
public class AmazonJobSourceClient extends BaseJobSourceClient implements JobSourceClient {

    private static final Logger LOG = Logger.getLogger(AmazonJobSourceClient.class);
    private static final String API_URL = "https://www.amazon.jobs/en/search.json?offset=0&result_limit=50&sort=relevant"
            +
            "&category[]=software-development&loc_query=%s&country=%s&base_query=software+engineer";

    @Override
    public boolean supports(String sourceType) {
        return "amazon".equals(sourceType);
    }

    @Override
    public PullResult crawl(PullTarget target) {
        try {
            JsonNode config = objectMapper.readTree(target.getScraperConfig());
            JsonNode locations = config.path("locations");

            if (!locations.isArray() || locations.isEmpty()) {
                return PullResult.success(crawlLocation(target, "", ""));
            }

            List<JobPost> allJobs = new ArrayList<>();
            for (JsonNode loc : locations) {
                String city = loc.path("city").asText("");
                String country = loc.path("country").asText("");
                allJobs.addAll(crawlLocation(target, city, country));
            }

            LOG.infof("Amazon: found %d jobs total", allJobs.size());
            return PullResult.success(allJobs);

        } catch (AmazonHttpException e) {
            LOG.warnf("Amazon %s/%s returned %d", e.city, e.country, e.httpStatus);
            return PullResult.failure(e.getMessage(), e.httpStatus);
        } catch (Exception e) {
            LOG.errorf(e, "Error crawling Amazon");
            return PullResult.failure(e.getMessage());
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private List<JobPost> crawlLocation(PullTarget target, String city, String country) throws Exception {
        HttpResponse<String> response = httpClient()
                .send(buildRequest(city, country), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new AmazonHttpException(city, country, response.statusCode(),
                    String.format("HTTP %d from %s", response.statusCode(), response.uri()));
        }

        return parseJobs(target, response.body(), city, country);
    }

    private HttpRequest buildRequest(String city, String country) {
        return HttpRequest.newBuilder()
                .uri(URI.create(String.format(API_URL, city, country)))
                .timeout(requestTimeout())
                .GET()
                .build();
    }

    private List<JobPost> parseJobs(PullTarget target, String body, String city, String country) throws Exception {
        List<JobPost> jobs = new ArrayList<>();
        JsonNode root = objectMapper.readTree(body);
        JsonNode jobsNode = root.path("jobs");

        if (!jobsNode.isArray())
            return jobs;

        for (JsonNode job : jobsNode) {
            String title = job.path("title").asText("");
            String jobCity = city.isBlank() ? job.path("city").asText("") : city;
            String jobCountry = country.isBlank() ? job.path("country_code").asText("") : country;
            String location = jobCity + ", " + jobCountry;
            String url = "https://www.amazon.jobs" + job.path("job_path").asText("");
            String desc = job.path("description").asText("");

            jobs.add(JobPost.builder()
                    .targetId(target.getId())
                    .title(title)
                    .url(url)
                    .city(jobCity)
                    .country(jobCountry)
                    .description(desc)
                    .languages(DEFAULT_LANGUAGES)
                    .contentHash(JobPost.computeHash(title, target.getCompanyName(), location, desc))
                    .build());
        }

        LOG.infof("Amazon %s/%s: found %d jobs", city, country, jobs.size());
        return jobs;
    }

    // ─── Private exception ───────────────────────────────────────────────────

    private static class AmazonHttpException extends RuntimeException {
        final String city;
        final String country;
        final int httpStatus;

        AmazonHttpException(String city, String country, int httpStatus, String message) {
            super(message);
            this.city = city;
            this.country = country;
            this.httpStatus = httpStatus;
        }
    }
}