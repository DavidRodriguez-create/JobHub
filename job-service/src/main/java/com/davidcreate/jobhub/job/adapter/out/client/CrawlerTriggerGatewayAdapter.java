package com.davidcreate.jobhub.job.adapter.out.client;

import com.davidcreate.jobhub.crawler.contract.model.QueueTriggerRequest;
import com.davidcreate.jobhub.crawler.contract.model.TriggerRequestResponse;
import com.davidcreate.jobhub.job.domain.exception.CrawlerUnavailableException;
import com.davidcreate.jobhub.job.domain.exception.NoActiveTriggerException;
import com.davidcreate.jobhub.job.domain.exception.TriggerInProgressException;
import com.davidcreate.jobhub.job.domain.model.TriggerKind;
import com.davidcreate.jobhub.job.domain.model.TriggerRequest;
import com.davidcreate.jobhub.job.domain.model.TriggerStatus;
import com.davidcreate.jobhub.job.domain.port.out.CrawlerTriggerGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.UUID;

@ApplicationScoped
public class CrawlerTriggerGatewayAdapter implements CrawlerTriggerGateway {

    private static final Logger LOG = Logger.getLogger(CrawlerTriggerGatewayAdapter.class);

    private final CrawlerTriggerRestClient client;
    private final String serviceKey;

    public CrawlerTriggerGatewayAdapter(@RestClient CrawlerTriggerRestClient client,
                                         @ConfigProperty(name = "jobhub.internal.service-key") String serviceKey) {
        this.client = client;
        this.serviceKey = serviceKey;
    }

    @Override
    public TriggerRequest queue(TriggerKind kind, UUID requestedBy) {
        QueueTriggerRequest body = new QueueTriggerRequest()
                .kind(com.davidcreate.jobhub.crawler.contract.model.TriggerKind.fromValue(kind.value()))
                .requestedBy(requestedBy);
        try {
            return toDomain(client.queue(body, serviceKey));
        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            if (status == Response.Status.CONFLICT.getStatusCode()) {
                throw new TriggerInProgressException(kind);
            }
            LOG.errorf("crawler-service returned %d while queueing a %s pass", status, kind.value());
            throw new RuntimeException("crawler-service returned an unexpected error queueing a " + kind.value() + " pass", e);
        } catch (ProcessingException e) {
            LOG.errorf(e, "crawler-service unreachable while queueing a %s pass", kind.value());
            throw new CrawlerUnavailableException("The crawler service is not reachable, so the request "
                    + "could not be queued. Nothing was started. Try again in a moment.");
        }
    }

    @Override
    public TriggerRequest cancel(TriggerKind kind) {
        try {
            return toDomain(client.cancel(kind.value(), serviceKey));
        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            if (status == Response.Status.NOT_FOUND.getStatusCode()) {
                throw new NoActiveTriggerException(kind);
            }
            LOG.errorf("crawler-service returned %d while cancelling the %s pass", status, kind.value());
            throw new RuntimeException("crawler-service returned an unexpected error cancelling the " + kind.value() + " pass", e);
        } catch (ProcessingException e) {
            LOG.errorf(e, "crawler-service unreachable while cancelling the %s pass", kind.value());
            throw new CrawlerUnavailableException("The crawler service is not reachable, so the cancellation "
                    + "could not be recorded. Nothing was changed. Try again in a moment.");
        }
    }

    private static TriggerRequest toDomain(TriggerRequestResponse response) {
        return TriggerRequest.builder()
                .id(response.getId())
                .kind(TriggerKind.fromValue(response.getKind().toString()))
                .status(TriggerStatus.fromValue(response.getStatus().toString()))
                .requestedBy(response.getRequestedBy())
                .requestedAt(response.getRequestedAt())
                .finishedAt(response.getFinishedAt())
                .resultSummary(response.getResultSummary())
                .build();
    }
}
