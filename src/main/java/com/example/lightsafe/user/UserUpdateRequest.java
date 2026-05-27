package com.example.lightsafe.user;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateRequest {
    private String nickname;
    private String password;
    private String email;
    private String phone;

    public UserUpdateRequest() {}
}