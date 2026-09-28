package com.couponnumbergenerator.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    static final String KEYCLOAK_SCHEME = "keycloak";
    /** Machine-to-machine login for Business Central (and later, partners): paste client id + secret into Authorize. */
    public static final String CLIENT_CREDENTIALS_SCHEME = "keycloak-client-credentials";

    /**
     * Swagger UI's "Authorize" button logs in through Keycloak (Authorization Code + PKCE, client
     * {@code coupon-backend}, see the {@code springdoc.swagger-ui.oauth} block in application.yaml).
     * The browser talks to Keycloak directly, so these URLs must be the public issuer, not the
     * internal {@code http://keycloak:8080} the backend itself uses.
     */
    @Bean
    public OpenAPI openAPI(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        String oidc = issuerUri + "/protocol/openid-connect";
        return new OpenAPI()
                .info(new Info()
                        .title("Coupon Number Generator API")
                        .description("API for generating and managing fuel coupon numbers (Petrol: PU002X..., Diesel: PU006X...)")
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes(KEYCLOAK_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                                        .authorizationUrl(oidc + "/auth")
                                        .tokenUrl(oidc + "/token")
                                        .scopes(new Scopes()))))
                        .addSecuritySchemes(CLIENT_CREDENTIALS_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new OAuthFlows().clientCredentials(new OAuthFlow()
                                        .tokenUrl(oidc + "/token")
                                        .scopes(new Scopes())))))
                .addSecurityItem(new SecurityRequirement().addList(KEYCLOAK_SCHEME));
    }
}
