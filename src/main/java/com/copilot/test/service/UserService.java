package com.copilot.test.service;

import com.copilot.test.domain.User;
import com.copilot.test.dto.RegisterRequest;
import com.copilot.test.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final KeycloakAdminService keycloakAdminService;

    @Value("${keycloak.user.realm:sparring}")
    private String userRealm;

    public UserService(UserRepository userRepository, ProfileRepository profileRepository, KeycloakAdminService keycloakAdminService) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.keycloakAdminService = keycloakAdminService;
    }

    @Transactional
    public User register(RegisterRequest req) {
        // create in Keycloak
        String kcId = keycloakAdminService.createUser(userRealm, req.getUsername(), req.getEmail(), req.getPassword());

        // persist minimal local user record
        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setAuthServerId(kcId); // auth server id (Keycloak) saved for correlation
        User saved = userRepository.save(user);

        // create profile
        Profile profile = new Profile();
        profile.setUserId(saved.getId());
        profile.setDisplayName(req.getDisplayName() != null ? req.getDisplayName() : req.getUsername());
        profileRepository.save(profile);

        return saved;
    }
}
