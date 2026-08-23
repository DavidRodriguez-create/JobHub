package com.davidcreate.jobhub.job.domain.port.out;

import com.davidcreate.jobhub.job.domain.model.UnresolvedTarget;

import java.util.List;
import java.util.UUID;

public interface PullTargetRepository {

    List<UnresolvedTarget> findWithoutCompany(int limit);

    void assignCompany(UUID targetId, UUID companyId);
}
