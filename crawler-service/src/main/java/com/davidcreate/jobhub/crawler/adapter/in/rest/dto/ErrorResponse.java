package com.davidcreate.jobhub.crawler.adapter.in.rest.dto;

public record ErrorResponse(
        int status,
        String message) {
}
