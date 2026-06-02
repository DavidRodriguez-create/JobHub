package com.davidcreate.jobhub.application.adapter.out.client;

import com.davidcreate.jobhub.application.application.port.out.JobPostGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JobPostGatewayAdapter implements JobPostGateway {

    private static final Logger LOG = Logger.getLogger(JobPostGatewayAdapter.class);

    @Inject
    @RestClient
    JobServiceRestClient client;

    @Override
    public Optional<JobPostView> findById(UUID jobPostId) {
        try {
            JobPostRemoteResponse r = client.getById(jobPostId);
            return Optional.of(new JobPostView(
                    r.id(), r.title(), r.url(), r.description(), r.location()));
        } catch (NotFoundException nf) {
            return Optional.empty();
        } catch (WebApplicationException ex) {
            LOG.warnf("job-service returned %d for jobPostId=%s", ex.getResponse().getStatus(), jobPostId);
            return Optional.empty();
        }
    }
}
