package com.example.lightsafe.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public class UserRegisterRequest {

    @NotBlank(
            message = "아이디는 필수입니다."
    )
    @Size(
            max = 50,
            message = "아이디는 50자 이하여야 합니다."
    )
    private String username;

    @NotBlank(
            message = "이메일은 필수입니다."
    )
    @Email(
            message = "올바른 이메일 형식이 아닙니다."
    )
    @Size(
            max = 100,
            message = "이메일은 100자 이하여야 합니다."
    )
    private String email;

    @NotBlank(
            message = "비밀번호는 필수입니다."
    )
    @Size(
            min = 8,
            max = 72,
            message = "비밀번호는 8자 이상 72자 이하여야 합니다."
    )
    private String password;

    @NotBlank(
            message = "닉네임은 필수입니다."
    )
    @Size(
            max = 50,
            message = "닉네임은 50자 이하여야 합니다."
    )
    private String nickname;

    public UserRegisterRequest() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(
            String username
    ) {
        this.username =
                username == null
                        ? null
                        : username.trim();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(
            String email
    ) {
        this.email =
                email == null
                        ? null
                        : email
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(
            String password
    ) {
        /*
         * 비밀번호는 사용자가 의도적으로 공백을 넣을 수도 있으므로
         * 자동 trim 하지 않습니다.
         */
        this.password =
                password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(
            String nickname
    ) {
        this.nickname =
                nickname == null
                        ? null
                        : nickname.trim();
    }
}