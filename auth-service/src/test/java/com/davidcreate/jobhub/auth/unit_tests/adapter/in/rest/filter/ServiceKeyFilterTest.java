package com.davidcreate.jobhub.auth.unit_tests.adapter.in.rest.filter;

import com.davidcreate.jobhub.auth.adapter.in.rest.filter.ServiceKeyFilter;
import com.davidcreate.jobhub.auth.domain.exception.InvalidServiceKeyException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ServiceKeyFilter Unit Tests")
class ServiceKeyFilterTest {

    private static final String VALID_KEY = "test-internal-key";

    @Mock
    ContainerRequestContext requestContext;

    @Mock
    UriInfo uriInfo;

    private ServiceKeyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ServiceKeyFilter(VALID_KEY);
    }

    @Test
    @DisplayName("rejects requests to /internal/* with missing X-Service-Key header")
    void rejectsMissingHeader() {
        mockPath("internal/users/emails");
        when(requestContext.getHeaderString("X-Service-Key")).thenReturn(null);

        assertThatThrownBy(() -> filter.filter(requestContext))
                .isInstanceOf(InvalidServiceKeyException.class);
    }

    @Test
    @DisplayName("rejects requests to /internal/* with wrong X-Service-Key header")
    void rejectsWrongHeader() {
        mockPath("internal/users/emails");
        when(requestContext.getHeaderString("X-Service-Key")).thenReturn("wrong-value");

        assertThatThrownBy(() -> filter.filter(requestContext))
                .isInstanceOf(InvalidServiceKeyException.class);
    }

    @Test
    @DisplayName("allows requests to /internal/* with correct X-Service-Key header")
    void allowsCorrectHeader() {
        mockPath("internal/users/emails");
        when(requestContext.getHeaderString("X-Service-Key")).thenReturn(VALID_KEY);

        assertThatCode(() -> filter.filter(requestContext)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("does not check the header for non-internal paths")
    void ignoresNonInternalPaths() {
        mockPath("account");

        assertThatCode(() -> filter.filter(requestContext)).doesNotThrowAnyException();
    }

    private void mockPath(String path) {
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn(path);
    }
}
