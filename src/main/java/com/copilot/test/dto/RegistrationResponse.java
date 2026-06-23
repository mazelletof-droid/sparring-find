package com.copilot.test.dto;

public class RegistrationResponse {
    private UserResponse user;
    private ProfileResponse profile;

    public UserResponse getUser() { return user; }
    public void setUser(UserResponse user) { this.user = user; }
    public ProfileResponse getProfile() { return profile; }
    public void setProfile(ProfileResponse profile) { this.profile = profile; }
}
