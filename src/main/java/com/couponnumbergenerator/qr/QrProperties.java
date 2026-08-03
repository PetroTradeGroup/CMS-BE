package com.couponnumbergenerator.qr;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.qr")
public record QrProperties(
        @DefaultValue("change-me-in-production-qr-secret") String secretKey
) {}