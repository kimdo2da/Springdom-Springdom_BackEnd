package com.example.lightsafe.user.dto;

public class UserUpdateRequest {
    private String nickname;
    private String password;

    public UserUpdateRequest() {}

    public String getNickname() { return nickname; }
    public String getPassword() { return password; }
}