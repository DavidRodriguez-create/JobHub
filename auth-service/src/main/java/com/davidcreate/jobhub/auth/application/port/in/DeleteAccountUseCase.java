package com.davidcreate.jobhub.auth.application.port.in;

import java.util.UUID;

public interface DeleteAccountUseCase {

    /**
     * Permanently delete the account after validating a {@code delete-account}
     * verification code. Throws if the code is missing, wrong, used, or expired.
     */
    void delete(UUID userId, UUID verificationId, String code);
}
