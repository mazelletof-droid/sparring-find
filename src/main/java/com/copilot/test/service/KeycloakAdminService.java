package com.copilot.test.service;

import jakarta.annotation.PostConstruct;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.ws.rs.core.Response;

@Service
public class KeycloakAdminService {

    @Value("${keycloak.admin.url:http://localhost:8080}")
    private String serverUrl;

    @Value("${keycloak.admin.realm:master}")
    private String adminRealm;

    @Value("${keycloak.admin.client-id:admin-cli}")
    private String clientId;

    @Value("${keycloak.admin.client-secret:}")
    private String clientSecret;

    @Value("${keycloak.admin.username:}")
    private String adminUsername;

    @Value("${keycloak.admin.password:}")
    private String adminPassword;

    private Keycloak keycloak;

    // Allow injection for tests
    public KeycloakAdminService() {
    }

    // Constructor for injecting a pre-built Keycloak client (useful for tests or integration setups)
    public KeycloakAdminService(Keycloak keycloak) {
        this.keycloak = keycloak;
    }

    @PostConstruct
    public void init() {
        if (this.keycloak != null) {
            // already provided (e.g., in tests)
            return;
        }

        KeycloakBuilder builder = KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(adminRealm)
                .clientId(clientId);

        // Prefer password grant if admin username/password provided (useful for testcontainers)
        if (adminUsername != null && !adminUsername.isEmpty() && adminPassword != null && !adminPassword.isEmpty()) {
            builder.grantType(OAuth2Constants.PASSWORD)
                    .username(adminUsername)
                    .password(adminPassword);
        } else {
            builder.grantType(OAuth2Constants.CLIENT_CREDENTIALS);
            if (clientSecret != null && !clientSecret.isEmpty()) {
                builder.clientSecret(clientSecret);
            }
        }
        this.keycloak = builder.build();
    }

    /**
     * Create a Keycloak user in the configured realm and set an initial password.
     * Returns the created user id.
     */
    public String createUser(String realm, String username, String email, String password) {
        RealmResource realmResource = keycloak.realm(realm);

        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setEnabled(true);

        Response response = realmResource.users().create(user);
        if (response.getStatus() != 201 && response.getStatus() != 204) {
            throw new IllegalStateException("Failed to create user in Keycloak: HTTP " + response.getStatus());
        }
        String userId = CreatedResponseUtil.getCreatedId(response);

        // set password
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setTemporary(false);
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);

        UserResource userResource = realmResource.users().get(userId);
        userResource.resetPassword(credential);

        return userId;
    }

    public void deleteUser(String realm, String userId) {
        keycloak.realm(realm).users().get(userId).remove();
    }
}
