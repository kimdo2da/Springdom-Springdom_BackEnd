package com.example.lightsafe.dto;

public class UserUpdateRequest {
    private String nickname;
    private String password;

    public UserUpdateRequest() {}

    public String getNickname() { return nickname; }
    public String getPassword() { return password; }
}