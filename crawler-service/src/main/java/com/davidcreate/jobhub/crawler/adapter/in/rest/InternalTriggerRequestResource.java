package com.davidcreate.jobhub.crawler.adapter.in.rest;

import com.davidcreate.jobhub.crawler.adapter.in.rest.dto.TriggerRequestResponseMapper;
import com.davidcreate.jobhub.crawler.contract.api.InternalApi;
import com.davidcreate.jobhub.crawler.contract.model.QueueTriggerRequest;
import com.davidcreate.jobhub.crawler.domain.exception.ValidationException;
import com.davidcreate.jobhub.crawler.domain.model.TriggerKind;
import com.davidcreate.jobhub.crawler.domain.model.TriggerOrigin;
import com.davidcreate.jobhub.crawler.domain.model.TriggerRequest;
import com.davidcreate.jobhub.crawler.domain.port.in.CancelTriggerRequestUseCase;
import com.davidcreate.jobhub.crawler.domain.port.in.QueueTriggerRequestUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Internal, service-to-service surface of {@code crawler.trigger_request} writes (story
 * #582, ADR 0033). Guarded by {@link com.davidcreate.jobhub.crawler.adapter.in.rest.filter.ServiceKeyFilter},
 * not a JWT: crawler-service has no published port and no JWT stack. Implements the
 * generated {@link InternalApi} interface (contract-first, api-contracts).
 */
@ApplicationScoped
public class InternalTriggerRequestResource implements InternalApi {

    private final QueueTriggerRequestUseCase queueUseCase;
    private final CancelTriggerRequestUseCase cancelUseCase;

    public InternalTriggerRequestResource(QueueTriggerRequestUseCase queueUseCase,
            CancelTriggerRequestUseCase cancelUseCase) {
        this.queueUseCase = queueUseCase;
        this.cancelUseCase = cancelUseCase;
    }

    @Override
    public Response queueTriggerRequest(QueueTriggerRequest queueTriggerRequest) {
        if (queueTriggerRequest == null) {
            throw new ValidationException("request body is required");
        }
        if (queueTriggerRequest.getKind() == null) {
            throw new ValidationException("kind is required");
        }
        TriggerKind domainKind = TriggerKind.fromValue(queueTriggerRequest.getKind().toString());
        TriggerOrigin domainOrigin = queueTriggerRequest.getOrigin() == null
                ? null
                : TriggerOrigin.fromValue(queueTriggerRequest.getOrigin().toString());

        TriggerRequest queued = queueUseCase.queue(domainKind, domainOrigin, queueTriggerRequest.getRequestedBy());
        return Response.status(Response.Status.ACCEPTED)
                .type(MediaType.APPLICATION_JSON)
                .entity(TriggerRequestResponseMapper.toResponse(queued))
                .build();
    }

    @Override
    public Response cancelTriggerRequest(com.davidcreate.jobhub.crawler.contract.model.TriggerKind kind) {
        TriggerKind domainKind = TriggerKind.fromValue(kind.toString());
        TriggerRequest cancelled = cancelUseCase.cancel(domainKind);
        return Response.ok(TriggerRequestResponseMapper.toResponse(cancelled))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
