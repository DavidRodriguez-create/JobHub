package com.davidcreate.jobhub.notification.unit_tests.adapter.out.client.auth;

import com.davidcreate.jobhub.auth.contract.model.UserEmailBatchResponse;
import com.davidcreate.jobhub.auth.contract.model.UserEmailEntry;
import com.davidcreate.jobhub.notification.adapter.out.client.auth.AuthInternalRestClient;
import com.davidcreate.jobhub.notification.adapter.out.client.auth.UserEmailGatewayAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserEmailGatewayAdapter Unit Tests")
class UserEmailGatewayAdapterTest {

    @Mock
    AuthInternalRestClient restClient;

    UserEmailGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserEmailGatewayAdapter(restClient, "test-service-key");
    }

    // TC-29
    @Test
    @DisplayName("user_email_adapter_maps_batch_response_entries_to_map")
    void mapsBatchResponseEntriesToMap() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();

        UserEmailEntry entry1 = new UserEmailEntry();
        entry1.setUserId(u1);
        entry1.setEmail("u1@example.com");

        UserEmailEntry entry2 = new UserEmailEntry();
        entry2.setUserId(u2);
        entry2.setEmail("u2@example.com");

        UserEmailBatchResponse response = new UserEmailBatchResponse();
        response.setEmails(List.of(entry1, entry2));

        when(restClient.getUserEmails(any(), any())).thenReturn(response);

        Map<UUID, String> result = adapter.fetchEmails(Set.of(u1, u2, u3));

        assertThat(result).containsEntry(u1, "u1@example.com");
        assertThat(result).containsEntry(u2, "u2@example.com");
        assertThat(result).doesNotContainKey(u3);
        assertThat(result).hasSize(2);
    }
}
