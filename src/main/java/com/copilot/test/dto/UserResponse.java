package com.copilot.test.dto;

public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String authServerId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAuthServerId() { return authServerId; }
    public void setAuthServerId(String authServerId) { this.authServerId = authServerId; }
}
