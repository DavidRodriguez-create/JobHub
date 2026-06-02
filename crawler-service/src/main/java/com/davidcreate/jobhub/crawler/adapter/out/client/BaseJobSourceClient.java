package com.davidcreate.jobhub.crawler.adapter.out.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.http.HttpClient;
import java.time.Duration;

public abstract class BaseJobSourceClient {

    @Inject
    protected ObjectMapper objectMapper;

    @ConfigProperty(name = "crawler.http.connect-timeout-seconds", defaultValue = "10")
    int connectTimeoutSeconds;

    @ConfigProperty(name = "crawler.http.request-timeout-seconds", defaultValue = "30")
    int requestTimeoutSeconds;

    protected HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
    }

    protected Duration requestTimeout() {
        return Duration.ofSeconds(requestTimeoutSeconds);
    }
}