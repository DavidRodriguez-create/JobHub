package com.davidcreate.jobhub.notification.unit_tests.adapter.out.client.application;

import com.davidcreate.jobhub.application.contract.model.InterestProfileResponse;
import com.davidcreate.jobhub.notification.adapter.out.client.application.AppInternalRestClient;
import com.davidcreate.jobhub.notification.adapter.out.client.application.InterestProfileGatewayAdapter;
import com.davidcreate.jobhub.notification.domain.model.InterestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InterestProfileGatewayAdapter Unit Tests")
class InterestProfileGatewayAdapterTest {

    @Mock
    AppInternalRestClient restClient;

    InterestProfileGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new InterestProfileGatewayAdapter(restClient, "test-service-key");
    }

    // TC-27
    @Test
    @DisplayName("interest_profile_adapter_maps_rest_response_to_domain")
    void mapsRestResponseToDomain() {
        UUID userId = UUID.randomUUID();
        InterestProfileResponse response = new InterestProfileResponse();
        response.setUserId(userId);
        response.setLocations(List.of("Barcelona, Spain"));
        response.setCompanies(List.of("Acme Corp"));
        response.setKeywords(List.of("backend", "java", "developer"));

        when(restClient.getUserInterestProfile(eq(userId), any())).thenReturn(response);

        InterestProfile profile = adapter.fetch(userId);

        assertThat(profile.getLocations()).containsExactly("Barcelona, Spain");
        assertThat(profile.getCompanies()).containsExactly("Acme Corp");
        assertThat(profile.getKeywords()).containsExactly("backend", "java", "developer");

        verify(restClient).getUserInterestProfile(eq(userId), eq("test-service-key"));
    }

    // TC-28
    @Test
    @DisplayName("interest_profile_adapter_maps_empty_arrays_to_empty_profile")
    void mapsEmptyArraysToEmptyProfile() {
        UUID userId = UUID.randomUUID();
        InterestProfileResponse response = new InterestProfileResponse();
        response.setUserId(userId);
        response.setLocations(List.of());
        response.setCompanies(List.of());
        response.setKeywords(List.of());

        when(restClient.getUserInterestProfile(eq(userId), any())).thenReturn(response);

        InterestProfile profile = adapter.fetch(userId);

        assertThat(profile.getLocations()).isEmpty();
        assertThat(profile.getCompanies()).isEmpty();
        assertThat(profile.getKeywords()).isEmpty();
        assertThat(profile.isEmpty()).isTrue();
    }
}
