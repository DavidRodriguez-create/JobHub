package com.davidcreate.jobhub.crawler.domain.port.out;

import com.davidcreate.jobhub.crawler.domain.model.PullTarget;

import java.util.Optional;
import java.util.UUID;

public interface PullTargetRepository {
    Optional<PullTarget> findNextAvailableAndLock();

    Optional<PullTarget> findAndLockById(UUID id);

    Optional<PullTarget> findTargetById(UUID id);

    void save(PullTarget target);
}