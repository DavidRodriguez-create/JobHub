package com.davidcreate.jobhub.notification.domain.port.out;

import java.util.List;
import java.util.UUID;

public interface TwoFactorStatusGateway {

    List<UUID> fetchUsersWithoutTwoFactor();
}
