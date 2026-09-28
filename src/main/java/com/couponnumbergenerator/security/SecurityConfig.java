package com.couponnumbergenerator.security;

import com.couponnumbergenerator.service.SecurityAuditService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.convert.converter.Converter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final String jwkSetUri;
    private final String issuerUri;
    private final String allowedOrigins;
    private final SecurityAuditService securityAuditService;

    /**
     * Off only in the local profile: a local Keycloak stamps each token's "iss" with whatever host
     * the client used (localhost for the browser, the Wi-Fi IP for a phone), so pinning one issuer
     * meant editing config whenever the IP changed. The signature is still checked against the
     * local Keycloak's keys.
     */
    @Value("${app.security.validate-issuer:true}")
    private boolean validateIssuer = true;

    public SecurityConfig(@Value("${app.security.jwk-set-uri}") String jwkSetUri,
                           @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
                           @Value("${app.cors.allowed-origins}") String allowedOrigins,
                           SecurityAuditService securityAuditService) {
        this.jwkSetUri = jwkSetUri;
        this.issuerUri = issuerUri;
        this.allowedOrigins = allowedOrigins;
        this.securityAuditService = securityAuditService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder()).jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint()));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(validateIssuer
                ? JwtValidators.createDefaultWithIssuer(issuerUri)
                : JwtValidators.createDefault());
        return decoder;
    }

    /**
     * Spring Security's default JWT-to-authorities conversion reads the {@code scope}/{@code scp}
     * claim, which Keycloak doesn't populate for realm roles — roles live in the nested
     * {@code realm_access.roles} claim instead. Without this converter, every
     * {@code @PreAuthorize(hasRole(...))} check would see zero authorities and 403 unconditionally,
     * regardless of the caller's actual Keycloak roles.
     */
    private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::realmRoleAuthorities);
        return converter;
    }

    Collection<GrantedAuthority> realmRoleAuthorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    /** Mirrors WebConfig.addCorsMappings' origin list ("/api/**") so Security's pre-flight handling never disagrees with it. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    /** Hand-written body, matching {@link com.couponnumbergenerator.dto.response.ApiResponse#error} — a fixed
     * shape needs no ObjectMapper dependency (which would only complicate wiring this bean into test slices). */
    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            String authHeader = request.getHeader("Authorization");
            log.warn("401 on {} {} — Authorization header: {}, reason: {}",
                    request.getMethod(), request.getRequestURI(),
                    authHeader == null ? "absent" : (authHeader.startsWith("Bearer ")
                            ? "Bearer present, %d chars".formatted(authHeader.length() - 7)
                            : "present but not Bearer-prefixed"),
                    authException.getMessage());
            // No SecurityContext at this point — rejected before dispatch, so no Authentication to pass.
            securityAuditService.record(request, null, HttpServletResponse.SC_UNAUTHORIZED, authException.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"success\":false,\"message\":\"Authentication required\",\"data\":null}");
        };
    }
}
