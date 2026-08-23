package com.davidcreate.jobhub.application.domain.valueobject;

/**
 * Unified job info shown for an application. For crawled-job applications it is the
 * frozen snapshot; for manual entries it reflects the live user-entered job details.
 */
public record JobInfo(String title, String company, String location, String url, String companyLogoUrl) {
}
