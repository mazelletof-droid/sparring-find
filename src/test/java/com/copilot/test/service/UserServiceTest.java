package com.copilot.test.service;

import com.copilot.test.domain.User;
import com.copilot.test.dto.RegisterRequest;
import com.copilot.test.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    KeycloakAdminService keycloakAdminService;

    @InjectMocks
    UserService userService;

    @Test
    void register_shouldCreateKeycloakUserAndPersistLocalUser() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("secret");

        when(keycloakAdminService.createUser(anyString(), eq("alice"), eq("alice@example.com"), eq("secret")))
                .thenReturn("kc-123");

        ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
        User saved = new User();
        saved.setId(42L);
        saved.setUsername("alice");
        saved.setEmail("alice@example.com");
        saved.setAuthServerId("kc-123");
        when(userRepository.save(savedCaptor.capture())).thenReturn(saved);

        User result = userService.register(req);

        // verify Keycloak called
        verify(keycloakAdminService, times(1)).createUser(anyString(), eq("alice"), eq("alice@example.com"), eq("secret"));

        // verify saved user properties
        User toSave = savedCaptor.getValue();
        assertThat(toSave.getUsername()).isEqualTo("alice");
        assertThat(toSave.getEmail()).isEqualTo("alice@example.com");
        assertThat(toSave.getAuthServerId()).isEqualTo("kc-123");

        // verify returned entity is the repository result
        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getAuthServerId()).isEqualTo("kc-123");
    }
}
