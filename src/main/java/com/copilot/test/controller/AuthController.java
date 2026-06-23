package com.copilot.test.controller;

import com.copilot.test.dto.RegisterRequest;
import com.copilot.test.domain.User;
import com.copilot.test.domain.Profile;
import com.copilot.test.dto.UserResponse;
import com.copilot.test.dto.ProfileResponse;
import com.copilot.test.dto.RegisterRequest;
import com.copilot.test.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest req) {
        User created = userService.register(req);
        Profile profile = userService.getProfileForUser(created.getId());

        UserResponse ur = toUserResponse(created);
        ProfileResponse pr = toProfileResponse(profile);

        RegistrationResponse resp = new RegistrationResponse();
        resp.setUser(ur);
        resp.setProfile(pr);
        return ResponseEntity.ok(resp);
    }

    private UserResponse toUserResponse(User u) {
        UserResponse r = new UserResponse();
        r.setId(u.getId());
        r.setUsername(u.getUsername());
        r.setEmail(u.getEmail());
        r.setAuthServerId(u.getAuthServerId());
        return r;
    }

    private ProfileResponse toProfileResponse(Profile p) {
        if (p == null) return null;
        ProfileResponse pr = new ProfileResponse();
        pr.setId(p.getId());
        pr.setUserId(p.getUserId());
        pr.setDisplayName(p.getDisplayName());
        pr.setBio(p.getBio());
        pr.setLat(p.getLat());
        pr.setLon(p.getLon());
        pr.setSkillLevel(p.getSkillLevel());
        return pr;
    }
}
