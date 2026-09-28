package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.config.KeycloakAdminProperties;
import com.couponnumbergenerator.enums.UserSyncStatus;
import com.couponnumbergenerator.exception.KeycloakAdminException;
import com.couponnumbergenerator.model.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Exercises {@link KeycloakAdminClient} against a mocked Keycloak Admin REST API — every scenario
 * here must stay safe to retry, since {@code AttendantSyncSweep} calls the same methods again for
 * anything not yet SYNCED.
 */
class KeycloakAdminClientTest {

    private static final KeycloakAdminProperties PROPERTIES = new KeycloakAdminProperties(
            "http://keycloak.test", "petrotrade",
            new KeycloakAdminProperties.AdminClient("coupon-admin-api", "secret", 30));

    private MockRestServiceServer server;
    private KeycloakAdminClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(PROPERTIES.baseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KeycloakAdminClient(builder.build(), PROPERTIES);
    }

    private AppUser appUser() {
        return AppUser.builder()
                .username("jdoe")
                .email("jdoe@example.com")
                .firstName("Jane")
                .lastName("Doe")
                .locationCode("SITE-A")
                .syncStatus(UserSyncStatus.PENDING)
                .build();
    }

    private void expectToken() {
        server.expect(requestTo("http://keycloak.test/realms/petrotrade/protocol/openid-connect/token"))
                .andRespond(withSuccess("{\"access_token\":\"tok\"}", MediaType.APPLICATION_JSON));
    }

    private void expectJoinRetailGroup(String userId) {
        server.expect(requestTo("http://keycloak.test/admin/realms/petrotrade/group-by-path/RETAIL"))
                .andRespond(withSuccess("{\"id\":\"group-id\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://keycloak.test/admin/realms/petrotrade/users/" + userId + "/groups/group-id"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());
    }

    @Test
    void syncAttendantCreatesNewUserAndJoinsRetailGroupWhenUsernameIsFree() {
        expectToken();
        server.expect(requestTo("http://keycloak.test/admin/realms/petrotrade/users?username=jdoe&exact=true"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://keycloak.test/admin/realms/petrotrade/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .location(URI.create("http://keycloak.test/admin/realms/petrotrade/users/new-id")));
        expectJoinRetailGroup("new-id");

        KeycloakAdminClient.SyncResult result = client.syncAttendant(appUser());

        assertThat(result.keycloakId()).isEqualTo("new-id");
        assertThat(result.temporaryPassword()).isNotBlank();
        server.verify();
    }

    @Test
    void syncAttendantAdoptsAnAlreadyExistingUserWithoutMintingANewPassword() {
        // The common retry scenario: a prior attempt created the user in Keycloak but crashed
        // before the local AppUser row was marked SYNCED.
        expectToken();
        server.expect(requestTo("http://keycloak.test/admin/realms/petrotrade/users?username=jdoe&exact=true"))
                .andRespond(withSuccess("[{\"id\":\"existing-id\"}]", MediaType.APPLICATION_JSON));
        expectJoinRetailGroup("existing-id");

        KeycloakAdminClient.SyncResult result = client.syncAttendant(appUser());

        assertThat(result.keycloakId()).isEqualTo("existing-id");
        assertThat(result.temporaryPassword()).isNull();
        server.verify();
    }

    @Test
    void syncAttendantAdoptsExistingUserWhenCreateRacesIntoAConflict() {
        expectToken();
        server.expect(requestTo("http://keycloak.test/admin/realms/petrotrade/users?username=jdoe&exact=true"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://keycloak.test/admin/realms/petrotrade/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        server.expect(requestTo("http://keycloak.test/admin/realms/petrotrade/users?username=jdoe&exact=true"))
                .andRespond(withSuccess("[{\"id\":\"raced-id\"}]", MediaType.APPLICATION_JSON));
        expectJoinRetailGroup("raced-id");

        KeycloakAdminClient.SyncResult result = client.syncAttendant(appUser());

        assertThat(result.keycloakId()).isEqualTo("raced-id");
        assertThat(result.temporaryPassword()).isNull();
        server.verify();
    }

    @Test
    void syncAttendantWrapsAnUnreachableKeycloakAsKeycloakAdminException() {
        server.expect(requestTo("http://keycloak.test/realms/petrotrade/protocol/openid-connect/token"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.syncAttendant(appUser()))
                .isInstanceOf(KeycloakAdminException.class);
    }

    @Test
    void resetTemporaryPasswordPutsANewCredentialAndReturnsIt() {
        expectToken();
        server.expect(requestTo("http://keycloak.test/admin/realms/petrotrade/users/existing-id/reset-password"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());

        String password = client.resetTemporaryPassword("existing-id");

        assertThat(password).isNotBlank();
        server.verify();
    }
}
