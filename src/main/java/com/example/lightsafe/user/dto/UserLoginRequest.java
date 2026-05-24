package com.example.lightsafe.user.dto;

public class UserLoginRequest {
    private String usernameOrEmail;
    private String password;

    public UserLoginRequest() {}

    public String getUsernameOrEmail() { return usernameOrEmail; }
    public String getPassword() { return password; }
}