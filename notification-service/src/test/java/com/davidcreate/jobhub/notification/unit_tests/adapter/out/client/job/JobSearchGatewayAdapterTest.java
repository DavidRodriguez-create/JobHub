package com.davidcreate.jobhub.notification.unit_tests.adapter.out.client.job;

import com.davidcreate.jobhub.job.contract.model.CompanyInfo;
import com.davidcreate.jobhub.job.contract.model.JobPostSummary;
import com.davidcreate.jobhub.job.contract.model.JobSearchPage;
import com.davidcreate.jobhub.notification.adapter.out.client.job.JobSearchGatewayAdapter;
import com.davidcreate.jobhub.notification.adapter.out.client.job.JobServiceRestClient;
import com.davidcreate.jobhub.notification.domain.model.DigestJob;
import com.davidcreate.jobhub.notification.domain.model.JobSearchQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobSearchGatewayAdapter Unit Tests")
class JobSearchGatewayAdapterTest {

    @Mock
    JobServiceRestClient restClient;

    JobSearchGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JobSearchGatewayAdapter(restClient);
    }

    // TC-30
    @Test
    @DisplayName("job_search_adapter_maps_job_post_response_list_to_domain_job_summaries")
    void mapsJobPostResponseListToDomainJobSummaries() {
        UUID jobId1 = UUID.randomUUID();
        UUID jobId2 = UUID.randomUUID();

        CompanyInfo company1 = new CompanyInfo();
        company1.setName("Acme Corp");
        company1.setLogoUrl(URI.create("https://example.com/logo.png"));

        JobPostSummary job1 = new JobPostSummary();
        job1.setId(jobId1);
        job1.setTitle("Backend Developer");
        job1.setLocation("Barcelona, Spain");
        job1.setCompany(company1);

        CompanyInfo company2 = new CompanyInfo();
        company2.setName("Other Corp");
        company2.setLogoUrl(null);

        JobPostSummary job2 = new JobPostSummary();
        job2.setId(jobId2);
        job2.setTitle("Java Developer");
        job2.setLocation("Remote");
        job2.setCompany(company2);

        JobSearchPage page = new JobSearchPage();
        page.setContent(List.of(job1, job2));
        page.setPage(0);
        page.setSize(10);
        page.setTotalElements(2L);
        page.setTotalPages(1);

        when(restClient.searchJobs(any(), any(), anyString(), anyString(), anyInt())).thenReturn(page);

        JobSearchQuery query = JobSearchQuery.builder()
                .keyword("backend java developer")
                .locations(List.of("Barcelona, Spain"))
                .postedWithin("week")
                .sort("newest")
                .size(10)
                .build();

        List<DigestJob> result = adapter.search(query);

        assertThat(result).hasSize(2);

        DigestJob digestJob1 = result.get(0);
        assertThat(digestJob1.getId()).isEqualTo(jobId1);
        assertThat(digestJob1.getTitle()).isEqualTo("Backend Developer");
        assertThat(digestJob1.getCompanyName()).isEqualTo("Acme Corp");
        assertThat(digestJob1.getLocation()).isEqualTo("Barcelona, Spain");
        assertThat(digestJob1.getCompanyLogoUrl()).isEqualTo(URI.create("https://example.com/logo.png"));

        DigestJob digestJob2 = result.get(1);
        assertThat(digestJob2.getId()).isEqualTo(jobId2);
        assertThat(digestJob2.getCompanyName()).isEqualTo("Other Corp");
        assertThat(digestJob2.getCompanyLogoUrl()).isNull();
    }
}
