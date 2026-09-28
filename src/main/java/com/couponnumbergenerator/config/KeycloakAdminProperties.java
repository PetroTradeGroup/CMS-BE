package com.couponnumbergenerator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Credentials for our backend's own confidential service-account client ({@code coupon-admin-api})
 * — used only to call Keycloak's Admin REST API on a Team Leader's behalf when registering an
 * attendant. Never exposed to a browser or mobile client.
 */
@ConfigurationProperties(prefix = "app.keycloak")
public record KeycloakAdminProperties(
        @DefaultValue("http://192.168.0.112:8189") String baseUrl,
        @DefaultValue("petrotrade") String realm,
        AdminClient admin
) {
    public record AdminClient(
            @DefaultValue("coupon-admin-api") String clientId,
            @DefaultValue("") String clientSecret,
            @DefaultValue("30") int timeoutSeconds
    ) {}
}
