package com.davidcreate.jobhub.notification.unit_tests.adapter.out.client.application;

import com.davidcreate.jobhub.notification.adapter.out.client.application.AppInternalRestClient;
import com.davidcreate.jobhub.notification.adapter.out.client.application.ApplicationOwnershipGatewayAdapter;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApplicationOwnershipGatewayAdapter Unit Tests")
class ApplicationOwnershipGatewayAdapterTest {

    @Mock AppInternalRestClient restClient;
    @Mock Response response;

    private ApplicationOwnershipGatewayAdapter adapter;

    private final UUID applicationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new ApplicationOwnershipGatewayAdapter(restClient, "test-key");
    }

    // CR-U-100
    @Test
    @DisplayName("CR-U-100: 204 response returns true")
    void status204ReturnsTrue() {
        when(response.getStatus()).thenReturn(204);
        when(restClient.headOwner(any(), any(), any())).thenReturn(response);

        assertThat(adapter.isOwnedByUser(applicationId, userId)).isTrue();
    }

    // CR-U-101
    @Test
    @DisplayName("CR-U-101: 404 response returns false")
    void status404ReturnsFalse() {
        when(response.getStatus()).thenReturn(404);
        when(restClient.headOwner(any(), any(), any())).thenReturn(response);

        assertThat(adapter.isOwnedByUser(applicationId, userId)).isFalse();
    }
}
