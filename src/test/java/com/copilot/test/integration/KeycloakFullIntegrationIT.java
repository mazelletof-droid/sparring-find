package com.copilot.test.integration;

import com.copilot.test.dto.RegisterRequest;
import com.copilot.test.domain.User;
import com.copilot.test.repository.UserRepository;
import com.copilot.test.repository.ProfileRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.RealmRepresentation;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class KeycloakFullIntegrationIT {

    static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:21.1.1";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine")
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> keycloak = new GenericContainer<>(DockerImageName.parse(KEYCLOAK_IMAGE))
            .withExposedPorts(8080)
            .withEnv("KEYCLOAK_ADMIN","admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD","admin")
            .withCommand("start-dev");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("keycloak.admin.url", () -> "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080));
        registry.add("keycloak.admin.username", () -> "admin");
        registry.add("keycloak.admin.password", () -> "admin");
        registry.add("keycloak.user.realm", () -> "sparring");
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ProfileRepository profileRepository;

    static Keycloak kcAdmin;

    @BeforeAll
    static void beforeAll() throws Exception {
        // wait for Keycloak to be ready and create realm
        String url = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080) + "/realms";
        // Build Keycloak admin client to master realm using admin creds
        kcAdmin = KeycloakBuilder.builder()
                .serverUrl("http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080))
                .realm("master")
                .username("admin")
                .password("admin")
                .clientId("admin-cli")
                .build();

        // create realm 'sparring'
        RealmRepresentation realm = new RealmRepresentation();
        realm.setRealm("sparring");
        realm.setEnabled(true);
        try {
            kcAdmin.realms().create(realm);
        } catch (Exception ex) {
            // ignore if exists
        }
    }

    @AfterAll
    static void afterAll() {
        if (kcAdmin != null) kcAdmin.close();
    }

    @Test
    void full_register_creates_keycloak_user_and_persists() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("fullbob");
        req.setEmail("fullbob@example.com");
        req.setPassword("pwd");
        req.setDisplayName("Full Bob");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RegisterRequest> entity = new HttpEntity<>(req, headers);

        String url = "http://localhost:" + port + "/api/v1/auth/register";
        ResponseEntity<RegistrationResponse> resp = restTemplate.postForEntity(url, entity, RegistrationResponse.class);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();

        // verify in DB
        User u = userRepository.findByUsername("fullbob").orElseThrow();
        assertThat(u.getAuthServerId()).isNotNull();
        assertThat(profileRepository.findAll().stream().anyMatch(p -> p.getUserId().equals(u.getId()))).isTrue();

        // verify response
        RegistrationResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getUser().getUsername()).isEqualTo("fullbob");
        assertThat(body.getProfile().getDisplayName()).isEqualTo("Full Bob");

        // verify user exists in Keycloak
        boolean exists = kcAdmin.realm("sparring").users().search("fullbob").stream().anyMatch(r -> r.getUsername().equals("fullbob"));
        assertThat(exists).isTrue();
    }
}
