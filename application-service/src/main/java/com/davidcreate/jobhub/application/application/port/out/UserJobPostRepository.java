package com.davidcreate.jobhub.application.application.port.out;

import com.davidcreate.jobhub.application.domain.entity.UserJobPost;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserJobPostRepository {

    Optional<UserJobPost> findOneById(UUID id);

    List<UserJobPost> listByUser(UUID userId, int page, int size);

    long countByUser(UUID userId);

    UserJobPost save(UserJobPost post);

    void removeById(UUID id);

    void removeAllByUser(UUID userId);
}
