package com.davidcreate.jobhub.application.application.port.in;

/**
 * Manually-entered job details. Used both when creating a manual-entry application
 * and when patching its job info via {@code PATCH /applications/{id}/job}.
 */
public record JobDetailsCommand(String title, String company, String url, String location) {
}
