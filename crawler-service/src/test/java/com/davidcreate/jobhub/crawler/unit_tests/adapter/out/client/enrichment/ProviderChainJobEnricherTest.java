package com.davidcreate.jobhub.crawler.unit_tests.adapter.out.client.enrichment;

import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.ProviderChainJobEnricher;
import com.davidcreate.jobhub.crawler.adapter.out.client.enrichment.provider.EnrichmentProvider;
import com.davidcreate.jobhub.crawler.domain.exception.EnrichmentUnavailableException;
import com.davidcreate.jobhub.crawler.domain.model.JobEnrichment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProviderChainJobEnricher Unit Tests")
class ProviderChainJobEnricherTest {

    private static final JobEnrichment RESULT_1 = new JobEnrichment(
            "full-time", "senior", List.of("English"), List.of("Go"), "London", "United Kingdom", null, null);
    private static final JobEnrichment RESULT_2 = new JobEnrichment(
            "full-time", "mid", List.of("German"), List.of("Java"), "Berlin", "Germany", null, null);
    private static final JobEnrichment RESULT_3 = new JobEnrichment(
            "contract", "lead", List.of("French"), List.of("Rust"), "Paris", "France", null, null);

    @Mock
    EnrichmentProvider p1;

    @Mock
    EnrichmentProvider p2;

    @Mock
    EnrichmentProvider p3;

    private JobEnrichment enrich(ProviderChainJobEnricher enricher) {
        return enricher.enrich("Engineer", "Builds things in London.", "London", "United Kingdom");
    }

    @Test
    @DisplayName("TC-PC-01: first provider succeeds — chain stops, no fallback called")
    void firstProviderSucceeds() {
        when(p1.enrich(any(), any(), any(), any())).thenReturn(RESULT_1);

        ProviderChainJobEnricher enricher = new ProviderChainJobEnricher(List.of(p1, p2));

        JobEnrichment result = enrich(enricher);

        assertThat(result).isSameAs(RESULT_1);
        verify(p2, never()).enrich(any(), any(), any(), any());
    }

    @Test
    @DisplayName("TC-PC-02: first provider throws — chain falls through to second, which succeeds")
    void firstProviderThrowsSecondSucceeds() {
        when(p1.enrich(any(), any(), any(), any())).thenThrow(new RuntimeException("p1 down"));
        when(p2.enrich(any(), any(), any(), any())).thenReturn(RESULT_2);

        ProviderChainJobEnricher enricher = new ProviderChainJobEnricher(List.of(p1, p2));

        JobEnrichment result = enrich(enricher);

        assertThat(result).isSameAs(RESULT_2);
        verify(p1).enrich("Engineer", "Builds things in London.", "London", "United Kingdom");
        verify(p2).enrich("Engineer", "Builds things in London.", "London", "United Kingdom");
    }

    @Test
    @DisplayName("TC-PC-03: all providers throw — chain throws IllegalStateException")
    void allProvidersThrow() {
        when(p1.enrich(any(), any(), any(), any())).thenThrow(new RuntimeException("p1 down"));
        when(p2.enrich(any(), any(), any(), any())).thenThrow(new RuntimeException("p2 down"));

        ProviderChainJobEnricher enricher = new ProviderChainJobEnricher(List.of(p1, p2));

        assertThatThrownBy(() -> enrich(enricher))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("All enrichment providers exhausted");

        verify(p1).enrich(any(), any(), any(), any());
        verify(p2).enrich(any(), any(), any(), any());
    }

    @Test
    @DisplayName("TC-PC-04: empty provider list — throws immediately without invoking anything")
    void emptyProviderListThrows() {
        ProviderChainJobEnricher enricher = new ProviderChainJobEnricher(List.of());

        assertThatThrownBy(() -> enrich(enricher))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("All enrichment providers exhausted");
    }

    @Test
    @DisplayName("TC-PC-05: chain iteration order is declaration order, not insertion-into-map order")
    void chainIterationOrderIsDeclarationOrder() {
        when(p1.enrich(any(), any(), any(), any())).thenThrow(new RuntimeException("p1 down"));
        when(p2.enrich(any(), any(), any(), any())).thenThrow(new RuntimeException("p2 down"));
        when(p3.enrich(any(), any(), any(), any())).thenReturn(RESULT_3);

        ProviderChainJobEnricher enricher = new ProviderChainJobEnricher(List.of(p1, p2, p3));

        JobEnrichment result = enrich(enricher);

        assertThat(result).isSameAs(RESULT_3);
        InOrder inOrder = inOrder(p1, p2, p3);
        inOrder.verify(p1).enrich(any(), any(), any(), any());
        inOrder.verify(p2).enrich(any(), any(), any(), any());
        inOrder.verify(p3).enrich(any(), any(), any(), any());
    }

    @Test
    @DisplayName("TC-PC-06: a single-element list where the provider succeeds")
    void singleElementListSucceeds() {
        when(p1.enrich(any(), any(), any(), any())).thenReturn(RESULT_1);

        ProviderChainJobEnricher enricher = new ProviderChainJobEnricher(List.of(p1));

        JobEnrichment result = enrich(enricher);

        assertThat(result).isSameAs(RESULT_1);
    }

    @Test
    @DisplayName("TC-PC-07: exception from a provider does not leak provider-internal exception type")
    void exceptionFromProviderIsSwallowed() {
        when(p1.enrich(any(), any(), any(), any()))
                .thenThrow(new jakarta.ws.rs.WebApplicationException("p1 unavailable"));
        when(p2.enrich(any(), any(), any(), any())).thenReturn(RESULT_2);

        ProviderChainJobEnricher enricher = new ProviderChainJobEnricher(List.of(p1, p2));

        JobEnrichment result = enrich(enricher);

        assertThat(result).isSameAs(RESULT_2);
    }

    @Test
    @DisplayName("C7: every provider fails transiently — chain throws EnrichmentUnavailableException")
    void allProvidersFailTransiently() {
        when(p1.enrich(any(), any(), any(), any()))
                .thenThrow(new EnrichmentUnavailableException("p1 transiently unavailable"));
        when(p2.enrich(any(), any(), any(), any()))
                .thenThrow(new EnrichmentUnavailableException("p2 transiently unavailable"));

        ProviderChainJobEnricher enricher = new ProviderChainJobEnricher(List.of(p1, p2));

        assertThatThrownBy(() -> enrich(enricher))
                .isInstanceOf(EnrichmentUnavailableException.class);

        verify(p1).enrich(any(), any(), any(), any());
        verify(p2).enrich(any(), any(), any(), any());
    }

    @Test
    @DisplayName("C8: one provider had a genuine content failure, the rest transient — chain throws, not EnrichmentUnavailableException")
    void mixedGenuineAndTransientFailuresIsNotUnavailable() {
        when(p1.enrich(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("p1 reachable but unusable output"));
        when(p2.enrich(any(), any(), any(), any()))
                .thenThrow(new EnrichmentUnavailableException("p2 transiently unavailable"));

        ProviderChainJobEnricher enricher = new ProviderChainJobEnricher(List.of(p1, p2));

        assertThatThrownBy(() -> enrich(enricher))
                .isNotInstanceOf(EnrichmentUnavailableException.class)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("All enrichment providers exhausted");

        verify(p1).enrich(any(), any(), any(), any());
        verify(p2).enrich(any(), any(), any(), any());
    }

    @Test
    @DisplayName("C9: first provider throws transiently, second succeeds — non-null result returned, no regression")
    void firstProviderTransientSecondSucceeds() {
        when(p1.enrich(any(), any(), any(), any()))
                .thenThrow(new EnrichmentUnavailableException("p1 transiently unavailable"));
        when(p2.enrich(any(), any(), any(), any())).thenReturn(RESULT_2);

        ProviderChainJobEnricher enricher = new ProviderChainJobEnricher(List.of(p1, p2));

        JobEnrichment result = enrich(enricher);

        assertThat(result).isNotNull().isSameAs(RESULT_2);
    }
}
