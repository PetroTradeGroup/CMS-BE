package com.couponnumbergenerator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.erp")
public record ErpProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("false") boolean mock,
        @DefaultValue("http://localhost:9090") String baseUrl,
        @DefaultValue("/api/redemptions") String redemptionEndpoint,
        @DefaultValue("") String apiKey,
        @DefaultValue("30") int timeoutSeconds,
        @DefaultValue("300000") long retryIntervalMs
) {}