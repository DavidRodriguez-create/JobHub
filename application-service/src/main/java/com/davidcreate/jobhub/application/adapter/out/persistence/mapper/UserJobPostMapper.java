package com.davidcreate.jobhub.application.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.application.adapter.out.persistence.entity.UserJobPostEntity;
import com.davidcreate.jobhub.application.domain.entity.UserJobPost;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserJobPostMapper {

    public UserJobPost toDomain(UserJobPostEntity e) {
        return UserJobPost.builder()
                .id(e.id)
                .userId(e.userId)
                .title(e.title)
                .company(e.company)
                .url(e.url)
                .location(e.location)
                .createdAt(e.createdAt)
                .updatedAt(e.updatedAt)
                .build();
    }

    public UserJobPostEntity toEntity(UserJobPost u) {
        UserJobPostEntity e = new UserJobPostEntity();
        e.id = u.getId();
        e.userId = u.getUserId();
        e.title = u.getTitle();
        e.company = u.getCompany();
        e.url = u.getUrl();
        e.location = u.getLocation();
        e.createdAt = u.getCreatedAt();
        e.updatedAt = u.getUpdatedAt();
        return e;
    }

    public void updateEntity(UserJobPostEntity e, UserJobPost u) {
        e.title = u.getTitle();
        e.company = u.getCompany();
        e.url = u.getUrl();
        e.location = u.getLocation();
    }
}
