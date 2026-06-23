package com.copilot.test.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.core.Response;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeycloakAdminServiceTest {

    @Mock
    Keycloak keycloak;

    @Mock
    RealmResource realmResource;

    @Mock
    UsersResource usersResource;

    @Mock
    UserResource userResource;

    @Test
    void createUser_shouldReturnCreatedId_andResetPassword() {
        // Arrange
        when(keycloak.realm("test-realm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);

        // Simulate created response with Location header containing the new id
        Response createdResponse = Response.created(URI.create("http://localhost/users/abc-123")).build();
        when(usersResource.create(any())).thenReturn(createdResponse);

        when(realmResource.users().get("abc-123")).thenReturn(userResource);

        KeycloakAdminService svc = new KeycloakAdminService(keycloak);

        // Act
        String id = svc.createUser("test-realm", "bob", "bob@example.com", "pwd123");

        // Assert
        assertThat(id).isEqualTo("abc-123");
        // verify resetPassword called on the user resource
        ArgumentCaptor<CredentialRepresentation> credCaptor = ArgumentCaptor.forClass(CredentialRepresentation.class);
        verify(userResource, times(1)).resetPassword(credCaptor.capture());
        CredentialRepresentation cred = credCaptor.getValue();
        assertThat(cred.getValue()).isEqualTo("pwd123");
    }
}
