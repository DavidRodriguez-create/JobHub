package com.davidcreate.jobhub.crawler.adapter.in.rest.dto;

/**
 * Standard error response body. Shape: {"error": "Human Title", "message": "detail"}.
 */
public record ErrorResponse(
        String error,
        String message) {
}
