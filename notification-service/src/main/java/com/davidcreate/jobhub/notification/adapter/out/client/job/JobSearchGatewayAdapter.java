package com.davidcreate.jobhub.notification.adapter.out.client.job;

import com.davidcreate.jobhub.job.contract.model.JobPostSummary;
import com.davidcreate.jobhub.job.contract.model.JobSearchPage;
import com.davidcreate.jobhub.notification.domain.model.DigestJob;
import com.davidcreate.jobhub.notification.domain.model.JobSearchQuery;
import com.davidcreate.jobhub.notification.domain.port.out.JobSearchGateway;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
public class JobSearchGatewayAdapter implements JobSearchGateway {

    private final JobServiceRestClient restClient;

    public JobSearchGatewayAdapter(@RestClient JobServiceRestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<DigestJob> search(JobSearchQuery query) {
        JobSearchPage page = restClient.searchJobs(query.getKeyword(), query.getLocations(),
                query.getPostedWithin(), query.getSort(), query.getSize());

        return page.getContent().stream()
                .map(this::toDomain)
                .toList();
    }

    private DigestJob toDomain(JobPostSummary response) {
        return DigestJob.builder()
                .id(response.getId())
                .title(response.getTitle())
                .companyName(response.getCompany() != null ? response.getCompany().getName() : null)
                .location(response.getLocation())
                .companyLogoUrl(response.getCompany() != null ? response.getCompany().getLogoUrl() : null)
                .build();
    }
}
