package com.example.lightsafe.user;

public class UserRegisterRequest {
    private String username;
    private String email;
    private String password;
    private String nickname;

    public UserRegisterRequest() {}

    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getNickname() { return nickname; }
}