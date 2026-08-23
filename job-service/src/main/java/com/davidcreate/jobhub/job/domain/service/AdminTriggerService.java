package com.davidcreate.jobhub.job.domain.service;

import com.davidcreate.jobhub.job.domain.exception.TriggeringDisabledException;
import com.davidcreate.jobhub.job.domain.model.TriggerCommand;
import com.davidcreate.jobhub.job.domain.model.TriggerKind;
import com.davidcreate.jobhub.job.domain.model.TriggerRequest;
import com.davidcreate.jobhub.job.domain.model.TriggerStatusOverview;
import com.davidcreate.jobhub.job.domain.port.in.AdminTriggerUseCase;
import com.davidcreate.jobhub.job.domain.port.out.AdminTwoFactorGateway;
import com.davidcreate.jobhub.job.domain.port.out.CrawlerTriggerGateway;
import com.davidcreate.jobhub.job.domain.port.out.TriggerRequestRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class AdminTriggerService implements AdminTriggerUseCase {

    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$|^[a-zA-Z0-9]{8}$");

    private final TriggerRequestRepository repository;
    private final AdminTwoFactorGateway twoFactorGateway;
    private final CrawlerTriggerGateway crawlerTriggerGateway;
    private final boolean triggerEnabled;

    public AdminTriggerService(TriggerRequestRepository repository,
                                AdminTwoFactorGateway twoFactorGateway,
                                CrawlerTriggerGateway crawlerTriggerGateway,
                                @ConfigProperty(name = "jobhub.admin.trigger.enabled", defaultValue = "true") boolean triggerEnabled) {
        this.repository = repository;
        this.twoFactorGateway = twoFactorGateway;
        this.crawlerTriggerGateway = crawlerTriggerGateway;
        this.triggerEnabled = triggerEnabled;
    }

    @Override
    public TriggerRequest queue(TriggerCommand command) {
        if (!triggerEnabled) {
            throw new TriggeringDisabledException();
        }

        TriggerKind kind = parseKind(command.getKind());
        validateCode(command);

        // ADR 0019: the admin's own 2FA gates the trigger. Authorizes (returns
        // normally) whether the admin has no 2FA (code ignored) or has 2FA and
        // supplied a valid code; throws 422/429 otherwise.
        twoFactorGateway.verify(command.getRequestedBy(), command.getCode());

        // ADR 0033: crawler-service is the sole authority on the "one queued row per
        // kind" rule. job-service no longer pre-checks locally: it asks and maps
        // crawler-service's 409/unreachable response straight to the public contract.
        return crawlerTriggerGateway.queue(kind, command.getRequestedBy());
    }

    @Override
    public TriggerStatusOverview getStatus(UUID adminId) {
        boolean twoFactorRequired = twoFactorGateway.isEnabled(adminId);
        return TriggerStatusOverview.builder()
                .triggerEnabled(triggerEnabled)
                .twoFactorRequired(twoFactorRequired)
                .crawl(repository.findMostRecent(TriggerKind.CRAWL).orElse(null))
                .enrichment(repository.findMostRecent(TriggerKind.ENRICHMENT).orElse(null))
                .lastCrawlRun(repository.findLastFinished(TriggerKind.CRAWL).orElse(null))
                .lastEnrichmentRun(repository.findLastFinished(TriggerKind.ENRICHMENT).orElse(null))
                .build();
    }

    @Override
    public TriggerRequest cancel(TriggerKind kind) {
        // BR-384-7: cancel is never gated by 2FA — no call to twoFactorGateway here.
        // ADR 0033: crawler-service decides whether an active request exists; its
        // internal 404 is mapped back to the public 409 by the gateway adapter.
        return crawlerTriggerGateway.cancel(kind);
    }

    private static TriggerKind parseKind(String kind) {
        if (kind == null) {
            throw new BadRequestException("kind is required");
        }
        try {
            return TriggerKind.fromValue(kind);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private static void validateCode(TriggerCommand command) {
        if (command.getCode() != null && !CODE_PATTERN.matcher(command.getCode()).matches()) {
            throw new BadRequestException("code must be a 6-digit TOTP code or an 8-character backup code");
        }
    }
}
