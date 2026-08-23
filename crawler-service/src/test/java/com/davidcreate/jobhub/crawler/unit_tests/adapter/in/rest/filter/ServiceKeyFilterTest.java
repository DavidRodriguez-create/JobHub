package com.davidcreate.jobhub.crawler.unit_tests.adapter.in.rest.filter;

import com.davidcreate.jobhub.crawler.adapter.in.rest.filter.ServiceKeyFilter;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain JUnit 5 unit test using a hand-written {@code FakeRequestContext} rather than
 * Mockito mocks for {@code ContainerRequestContext}/{@code UriInfo} -- Mockito's inline
 * mock maker cannot reliably instrument {@code jakarta.ws.rs} interfaces when run in the
 * same Surefire fork as {@code @QuarkusTest} classes.
 */
@DisplayName("ServiceKeyFilter (story #582)")
class ServiceKeyFilterTest {

    private static final String EXPECTED_KEY = "test-internal-key";

    ServiceKeyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ServiceKeyFilter(EXPECTED_KEY);
    }

    @Test
    @DisplayName("allows the request through when X-Service-Key matches the configured value")
    void allowsMatchingKey() {
        FakeRequestContext requestContext = new FakeRequestContext(
                "internal/trigger-requests", EXPECTED_KEY);

        filter.filter(requestContext);

        assertThat(requestContext.aborted()).isFalse();
    }

    @Test
    @DisplayName("TR-08: aborts with 401 when X-Service-Key header is missing, resource never invoked")
    void rejectsMissingKey() {
        FakeRequestContext requestContext = new FakeRequestContext(
                "internal/trigger-requests", null);

        filter.filter(requestContext);

        assertThat(requestContext.aborted()).isTrue();
        assertThat(requestContext.abortResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("TR-09: aborts with 401 when X-Service-Key header does not match, resource never invoked")
    void rejectsWrongKey() {
        FakeRequestContext requestContext = new FakeRequestContext(
                "internal/trigger-requests", "wrong-value");

        filter.filter(requestContext);

        assertThat(requestContext.aborted()).isTrue();
        assertThat(requestContext.abortResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("does not check the header for non-internal paths")
    void passesThroughNonInternalPaths() {
        FakeRequestContext requestContext = new FakeRequestContext("crawl", null);

        filter.filter(requestContext);

        assertThat(requestContext.aborted()).isFalse();
    }

    /**
     * Minimal fake covering only the {@code ContainerRequestContext}/{@code UriInfo} methods
     * {@link ServiceKeyFilter} touches: {@code getUriInfo().getPath()}, {@code getHeaderString},
     * and {@code abortWith}.
     */
    private static final class FakeRequestContext implements jakarta.ws.rs.container.ContainerRequestContext {

        private final String path;
        private final String serviceKeyHeader;
        private Response abortResponse;

        FakeRequestContext(String path, String serviceKeyHeader) {
            this.path = path;
            this.serviceKeyHeader = serviceKeyHeader;
        }

        boolean aborted() {
            return abortResponse != null;
        }

        Response abortResponse() {
            return abortResponse;
        }

        @Override
        public jakarta.ws.rs.core.UriInfo getUriInfo() {
            return new jakarta.ws.rs.core.UriInfo() {
                @Override
                public String getPath() {
                    return path;
                }

                @Override
                public String getPath(boolean decode) {
                    return path;
                }

                @Override
                public java.util.List<jakarta.ws.rs.core.PathSegment> getPathSegments() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public java.util.List<jakarta.ws.rs.core.PathSegment> getPathSegments(boolean decode) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public java.net.URI getRequestUri() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public jakarta.ws.rs.core.UriBuilder getRequestUriBuilder() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public java.net.URI getAbsolutePath() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public jakarta.ws.rs.core.UriBuilder getAbsolutePathBuilder() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public java.net.URI getBaseUri() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public jakarta.ws.rs.core.UriBuilder getBaseUriBuilder() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public jakarta.ws.rs.core.MultivaluedMap<String, String> getPathParameters() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public jakarta.ws.rs.core.MultivaluedMap<String, String> getPathParameters(boolean decode) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public jakarta.ws.rs.core.MultivaluedMap<String, String> getQueryParameters() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public jakarta.ws.rs.core.MultivaluedMap<String, String> getQueryParameters(boolean decode) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public java.util.List<String> getMatchedURIs() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public java.util.List<String> getMatchedURIs(boolean decode) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public java.util.List<Object> getMatchedResources() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public java.net.URI resolve(java.net.URI uri) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public java.net.URI relativize(java.net.URI uri) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public String getHeaderString(String name) {
            if ("X-Service-Key".equals(name)) {
                return serviceKeyHeader;
            }
            return null;
        }

        @Override
        public void abortWith(Response response) {
            this.abortResponse = response;
        }

        // -- Unused ContainerRequestContext members --

        @Override
        public Object getProperty(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Collection<String> getPropertyNames() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setProperty(String name, Object object) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeProperty(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setRequestUri(java.net.URI requestUri) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setRequestUri(java.net.URI baseUri, java.net.URI requestUri) {
            throw new UnsupportedOperationException();
        }

        @Override
        public jakarta.ws.rs.core.Request getRequest() {
            throw new UnsupportedOperationException();
        }

        @Override
        public jakarta.ws.rs.core.SecurityContext getSecurityContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setSecurityContext(jakarta.ws.rs.core.SecurityContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getMethod() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setMethod(String method) {
            throw new UnsupportedOperationException();
        }

        @Override
        public jakarta.ws.rs.core.MultivaluedMap<String, String> getHeaders() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Date getDate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Locale getLanguage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getLength() {
            throw new UnsupportedOperationException();
        }

        @Override
        public jakarta.ws.rs.core.MediaType getMediaType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<jakarta.ws.rs.core.MediaType> getAcceptableMediaTypes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<java.util.Locale> getAcceptableLanguages() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Map<String, jakarta.ws.rs.core.Cookie> getCookies() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasEntity() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.io.InputStream getEntityStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setEntityStream(java.io.InputStream input) {
            throw new UnsupportedOperationException();
        }
    }
}
