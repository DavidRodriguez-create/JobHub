package com.davidcreate.jobhub.notification.unit_tests.adapter.out.client.application;

import com.davidcreate.jobhub.application.contract.model.ApplicationSummaryListResponse;
import com.davidcreate.jobhub.application.contract.model.ApplicationSummaryResponse;
import com.davidcreate.jobhub.notification.adapter.out.client.application.AppInternalRestClient;
import com.davidcreate.jobhub.notification.adapter.out.client.application.ApplicationSummaryGatewayAdapter;
import com.davidcreate.jobhub.notification.domain.model.ApplicationSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApplicationSummaryGatewayAdapter Unit Tests")
class ApplicationSummaryGatewayAdapterTest {

    @Mock
    AppInternalRestClient restClient;

    private ApplicationSummaryGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ApplicationSummaryGatewayAdapter(restClient, "test-key");
    }

    // NS244-U-01
    @Test
    @DisplayName("NS244-U-01: toDomain maps companyLogoUrl populated onto ApplicationSummary.getCompanyLogoUrl()")
    void toDomainMapsCompanyLogoUrlPopulated() {
        UUID appId = UUID.randomUUID();
        URI logoUrl = URI.create("https://cdn.example.com/acme.png");

        ApplicationSummaryResponse response = new ApplicationSummaryResponse()
                .applicationId(appId)
                .company("Acme Corp")
                .jobTitle("Senior Backend Engineer")
                .companyLogoUrl(logoUrl);

        ApplicationSummaryListResponse listResponse = new ApplicationSummaryListResponse()
                .items(List.of(response));

        when(restClient.getApplicationSummaries(anyString(), anyString())).thenReturn(listResponse);

        Map<UUID, ApplicationSummary> result = adapter.resolve(Set.of(appId));

        assertThat(result).containsKey(appId);
        assertThat(result.get(appId).getCompanyLogoUrl()).isEqualTo(logoUrl);
    }

    // NS244-U-02
    @Test
    @DisplayName("NS244-U-02: toDomain maps companyLogoUrl explicitly null onto null, company/jobTitle unaffected")
    void toDomainMapsCompanyLogoUrlNull() {
        UUID appId = UUID.randomUUID();

        ApplicationSummaryResponse response = new ApplicationSummaryResponse()
                .applicationId(appId)
                .company("Foo Inc")
                .jobTitle("Backend Dev")
                .companyLogoUrl(null);

        ApplicationSummaryListResponse listResponse = new ApplicationSummaryListResponse()
                .items(List.of(response));

        when(restClient.getApplicationSummaries(anyString(), anyString())).thenReturn(listResponse);

        Map<UUID, ApplicationSummary> result = assertDoesNotThrow(() -> adapter.resolve(Set.of(appId)));

        assertThat(result).containsKey(appId);
        ApplicationSummary summary = result.get(appId);
        assertThat(summary.getCompanyLogoUrl()).isNull();
        assertThat(summary.getCompany()).isEqualTo("Foo Inc");
        assertThat(summary.getJobTitle()).isEqualTo("Backend Dev");
    }

    // NS244-U-03
    @Test
    @DisplayName("NS244-U-03: toDomain maps a response that omits companyLogoUrl key entirely onto null, no exception")
    void toDomainMapsOmittedCompanyLogoUrlToNull() {
        UUID appId = UUID.randomUUID();

        // companyLogoUrl field on the generated model defaults to null when not set
        ApplicationSummaryResponse response = new ApplicationSummaryResponse()
                .applicationId(appId)
                .company("Acme Corp")
                .jobTitle("Senior Backend Engineer");
        // companyLogoUrl is NOT set - simulates an omitted key from older payload

        ApplicationSummaryListResponse listResponse = new ApplicationSummaryListResponse()
                .items(List.of(response));

        when(restClient.getApplicationSummaries(anyString(), anyString())).thenReturn(listResponse);

        Map<UUID, ApplicationSummary> result = assertDoesNotThrow(() -> adapter.resolve(Set.of(appId)));

        assertThat(result).containsKey(appId);
        assertThat(result.get(appId).getCompanyLogoUrl()).isNull();
    }
}
