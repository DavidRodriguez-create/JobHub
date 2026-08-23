package com.davidcreate.jobhub.auth.adapter.out.persistence.mapper;

import com.davidcreate.jobhub.auth.adapter.out.persistence.entity.ApplyProfileEntity;
import com.davidcreate.jobhub.auth.domain.entity.ApplyProfile;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ApplyProfileMapper {

    public ApplyProfile toDomain(ApplyProfileEntity entity) {
        return ApplyProfile.builder()
                .id(entity.id)
                .userId(entity.userId)
                .workAuthorization(entity.workAuthorization)
                .requiresSponsorship(entity.requiresSponsorship)
                .noticePeriod(entity.noticePeriod)
                .salaryExpectation(entity.salaryExpectation)
                .currentLocation(entity.currentLocation)
                .willingToRelocate(entity.willingToRelocate)
                .linkedinUrl(entity.linkedinUrl)
                .githubUrl(entity.githubUrl)
                .portfolioUrl(entity.portfolioUrl)
                .languages(entity.languages == null || entity.languages.isEmpty() ? null : entity.languages)
                .roomToGrow(entity.roomToGrow)
                .createdAt(entity.createdAt)
                .updatedAt(entity.updatedAt)
                .build();
    }

    public ApplyProfileEntity toEntity(ApplyProfile profile) {
        ApplyProfileEntity entity = new ApplyProfileEntity();
        entity.id = profile.getId();
        entity.userId = profile.getUserId();
        applyAnswerFields(entity, profile);
        entity.createdAt = profile.getCreatedAt();
        entity.updatedAt = profile.getUpdatedAt();
        return entity;
    }

    /** Updates only the answer-bank fields, leaving id/userId/createdAt untouched. */
    public void updateEntity(ApplyProfileEntity entity, ApplyProfile profile) {
        applyAnswerFields(entity, profile);
    }

    private void applyAnswerFields(ApplyProfileEntity entity, ApplyProfile profile) {
        entity.workAuthorization = profile.getWorkAuthorization();
        entity.requiresSponsorship = profile.getRequiresSponsorship();
        entity.noticePeriod = profile.getNoticePeriod();
        entity.salaryExpectation = profile.getSalaryExpectation();
        entity.currentLocation = profile.getCurrentLocation();
        entity.willingToRelocate = profile.getWillingToRelocate();
        entity.linkedinUrl = profile.getLinkedinUrl();
        entity.githubUrl = profile.getGithubUrl();
        entity.portfolioUrl = profile.getPortfolioUrl();
        entity.languages = profile.getLanguages() == null ? List.of() : profile.getLanguages();
        entity.roomToGrow = profile.getRoomToGrow();
    }
}
