package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.config.KeycloakAdminProperties;
import com.couponnumbergenerator.exception.KeycloakAdminException;
import com.couponnumbergenerator.model.AppUser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Calls Keycloak's Admin REST API (as the {@code coupon-admin-api} service account) to provision
 * attendant accounts. Every operation here is idempotent and safe to retry — callers
 * ({@code AttendantService}, {@code AttendantSyncSweep}) may call {@link #syncAttendant} any
 * number of times for the same {@link AppUser} until it reports SYNCED. The ATTENDANT role is
 * never taken from the caller — membership in the {@code /RETAIL} group is the only role grant
 * this client performs, and that group only carries ATTENDANT (see realm-export.json), so this
 * path can never produce a Team Leader or Admin account.
 */
@Service
public class KeycloakAdminClient {

    private static final String RETAIL_GROUP_PATH = "RETAIL";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final RestClient restClient;
    private final KeycloakAdminProperties properties;

    public KeycloakAdminClient(@Qualifier("keycloakAdminRestClient") RestClient restClient,
                                KeycloakAdminProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /** The outcome of syncing one {@link AppUser} to Keycloak. temporaryPassword is null when the user already existed. */
    public record SyncResult(String keycloakId, String temporaryPassword) {}

    /**
     * Creates {@code appUser} in Keycloak if it isn't there yet, joins it to {@code /RETAIL}, and
     * returns its Keycloak id. If a user with this username already exists — most likely a prior
     * attempt that created it in Keycloak but crashed before this AppUser row was marked SYNCED —
     * that user is adopted instead of erroring, and no new temporary password is minted for it.
     */
    public SyncResult syncAttendant(AppUser appUser) {
        String token = fetchServiceAccountToken();

        Optional<String> existingUserId = findUserIdByUsername(token, appUser.getUsername());
        if (existingUserId.isPresent()) {
            joinRetailGroup(token, existingUserId.get());
            return new SyncResult(existingUserId.get(), null);
        }

        String temporaryPassword = generateTemporaryPassword();
        Map<String, Object> body = Map.of(
                "username", appUser.getUsername(),
                "email", appUser.getEmail(),
                "firstName", appUser.getFirstName(),
                "lastName", appUser.getLastName(),
                "enabled", true,
                "emailVerified", false,
                "attributes", Map.of(
                        "department", List.of("RETAIL"),
                        "locationCode", List.of(appUser.getLocationCode())),
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", temporaryPassword,
                        "temporary", true)));

        URI location;
        try {
            location = restClient.post()
                    .uri("/admin/realms/{realm}/users", properties.realm())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders()
                    .getLocation();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                // Raced with another sync attempt (e.g. two overlapping sweep runs) — the user
                // landed in Keycloak between our exact-match lookup and this POST. Adopt it.
                String userId = findUserIdByUsername(token, appUser.getUsername())
                        .orElseThrow(() -> new KeycloakAdminException(
                                "Keycloak reported '%s' as a conflict but it cannot be found"
                                        .formatted(appUser.getUsername()), ex));
                joinRetailGroup(token, userId);
                return new SyncResult(userId, null);
            }
            throw new KeycloakAdminException(
                    "Keycloak rejected attendant creation for '%s': %s"
                            .formatted(appUser.getUsername(), ex.getMessage()), ex);
        } catch (RestClientException ex) {
            throw new KeycloakAdminException(
                    "Could not reach Keycloak to create attendant '%s': %s"
                            .formatted(appUser.getUsername(), ex.getMessage()), ex);
        }

        if (location == null) {
            throw new KeycloakAdminException(
                    "Keycloak did not return a Location header for the new user '%s'".formatted(appUser.getUsername()));
        }
        String userId = location.getPath().substring(location.getPath().lastIndexOf('/') + 1);

        joinRetailGroup(token, userId);

        return new SyncResult(userId, temporaryPassword);
    }

    /** Issues a fresh one-time password for an already-synced attendant — used when the original was never handed out. */
    public String resetTemporaryPassword(String keycloakId) {
        String token = fetchServiceAccountToken();
        String temporaryPassword = generateTemporaryPassword();
        try {
            restClient.put()
                    .uri("/admin/realms/{realm}/users/{userId}/reset-password", properties.realm(), keycloakId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("type", "password", "value", temporaryPassword, "temporary", true))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new KeycloakAdminException(
                    "Could not reset the temporary password for Keycloak user '%s': %s"
                            .formatted(keycloakId, ex.getMessage()), ex);
        }
        return temporaryPassword;
    }

    @SuppressWarnings("unchecked")
    private Optional<String> findUserIdByUsername(String token, String username) {
        List<Map<String, Object>> users;
        try {
            users = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/admin/realms/{realm}/users")
                            .queryParam("username", username)
                            .queryParam("exact", true)
                            .build(properties.realm()))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(List.class);
        } catch (RestClientException ex) {
            throw new KeycloakAdminException(
                    "Could not check Keycloak for existing user '%s': %s".formatted(username, ex.getMessage()), ex);
        }
        if (users == null || users.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(users.get(0).get("id")).map(Object::toString);
    }

    private void joinRetailGroup(String token, String userId) {
        String groupId;
        try {
            groupId = restClient.get()
                    .uri("/admin/realms/{realm}/group-by-path/{path}", properties.realm(), RETAIL_GROUP_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(Map.class)
                    .get("id")
                    .toString();
        } catch (RestClientException ex) {
            throw new KeycloakAdminException(
                    "Could not resolve the /RETAIL group in Keycloak: %s".formatted(ex.getMessage()), ex);
        }

        try {
            restClient.put()
                    .uri("/admin/realms/{realm}/users/{userId}/groups/{groupId}",
                            properties.realm(), userId, groupId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new KeycloakAdminException(
                    "Created user %s but failed to join /RETAIL group: %s".formatted(userId, ex.getMessage()), ex);
        }
    }

    private String fetchServiceAccountToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.admin().clientId());
        form.add("client_secret", properties.admin().clientSecret());

        try {
            Map<String, Object> response = restClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            Object accessToken = response == null ? null : response.get("access_token");
            if (accessToken == null) {
                throw new KeycloakAdminException("Keycloak token response had no access_token");
            }
            return accessToken.toString();
        } catch (RestClientException ex) {
            throw new KeycloakAdminException(
                    "Could not obtain a service-account token from Keycloak: %s".formatted(ex.getMessage()), ex);
        }
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
