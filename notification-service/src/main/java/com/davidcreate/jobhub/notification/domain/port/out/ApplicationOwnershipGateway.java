package com.davidcreate.jobhub.notification.domain.port.out;

import java.util.UUID;

public interface ApplicationOwnershipGateway {

    boolean isOwnedByUser(UUID applicationId, UUID userId);
}
