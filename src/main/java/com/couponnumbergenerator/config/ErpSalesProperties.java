package com.couponnumbergenerator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Namespaced separately from {@link ErpProperties} (redemption) so sales can go live independently. */
@ConfigurationProperties(prefix = "app.erp.sales")
public record ErpSalesProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("false") boolean mock,
        @DefaultValue("http://localhost:9091") String baseUrl,
        @DefaultValue("/api/coupon-sales/confirm") String confirmationEndpoint,
        @DefaultValue("") String inboundApiKey,
        @DefaultValue("30") int timeoutSeconds,
        @DefaultValue("300000") long retryIntervalMs
) {}