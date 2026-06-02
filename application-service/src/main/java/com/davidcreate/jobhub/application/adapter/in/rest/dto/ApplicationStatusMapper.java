package com.davidcreate.jobhub.application.adapter.in.rest.dto;

import com.davidcreate.jobhub.application.contract.model.ApplicationStatus;

/**
 * Bridges the contract status enum and the domain enum. Both carry the same eight
 * values; the contract's lowercase wire value matches {@code domain.dbValue()}.
 */
public final class ApplicationStatusMapper {

    private ApplicationStatusMapper() {
    }

    public static com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus toDomain(ApplicationStatus status) {
        return status == null ? null
                : com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus.fromDbValue(status.toString());
    }

    public static ApplicationStatus toContract(com.davidcreate.jobhub.application.domain.valueobject.ApplicationStatus status) {
        return status == null ? null : ApplicationStatus.fromValue(status.dbValue());
    }
}
