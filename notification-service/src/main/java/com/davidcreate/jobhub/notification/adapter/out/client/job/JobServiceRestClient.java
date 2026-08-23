package com.davidcreate.jobhub.notification.adapter.out.client.job;

import com.davidcreate.jobhub.job.contract.model.JobSearchPage;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "job-service")
@Path("/jobs")
public interface JobServiceRestClient {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    JobSearchPage searchJobs(@QueryParam("keyword") String keyword,
                              @QueryParam("location") List<String> location,
                              @QueryParam("postedWithin") String postedWithin,
                              @QueryParam("sort") String sort,
                              @QueryParam("size") int size);
}
