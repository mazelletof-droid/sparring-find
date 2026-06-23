package com.copilot.test.integration;

import com.copilot.test.dto.RegisterRequest;
import com.copilot.test.domain.User;
import com.copilot.test.repository.ProfileRepository;
import com.copilot.test.repository.UserRepository;
import com.copilot.test.service.KeycloakAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RegistrationIntegrationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ProfileRepository profileRepository;

    @MockBean
    KeycloakAdminService keycloakAdminService;

    @Test
    void register_endpoint_creates_user_and_profile() {
        when(keycloakAdminService.createUser(anyString(), anyString(), anyString(), anyString())).thenReturn("kc-999");

        RegisterRequest req = new RegisterRequest();
        req.setUsername("intbob");
        req.setEmail("intbob@example.com");
        req.setPassword("pwd");
        req.setDisplayName("Int Bob");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RegisterRequest> entity = new HttpEntity<>(req, headers);

        String url = "http://localhost:" + port + "/api/v1/auth/register";
        ResponseEntity<RegistrationResponse> resp = restTemplate.postForEntity(url, entity, RegistrationResponse.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();

        // verify in DB
        User u = userRepository.findByUsername("intbob").orElseThrow();
        assertThat(u.getAuthServerId()).isEqualTo("kc-999");
        assertThat(profileRepository.findAll().stream().anyMatch(p -> p.getUserId().equals(u.getId()))).isTrue();

        // verify response body
        RegistrationResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getUser().getUsername()).isEqualTo("intbob");
        assertThat(body.getProfile().getDisplayName()).isEqualTo("Int Bob");
    }
}
