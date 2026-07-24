package com.example.lightsafe.user;

import jakarta.validation.constraints.NotBlank;

import java.util.Locale;

public class UserLoginRequest {

    @NotBlank(
            message = "아이디 또는 이메일은 필수입니다."
    )
    private String usernameOrEmail;

    @NotBlank(
            message = "비밀번호는 필수입니다."
    )
    private String password;

    public UserLoginRequest() {
    }

    public String getUsernameOrEmail() {
        return usernameOrEmail;
    }

    public void setUsernameOrEmail(
            String usernameOrEmail
    ) {
        if (usernameOrEmail == null) {
            this.usernameOrEmail =
                    null;
            return;
        }

        String normalized =
                usernameOrEmail.trim();

        /*
         * 이메일 로그인은 대소문자 차이로 실패하지 않게 소문자로 맞춥니다.
         * username은 사용자가 정한 값을 그대로 유지합니다.
         */
        this.usernameOrEmail =
                normalized.contains("@")
                        ? normalized.toLowerCase(
                        Locale.ROOT
                )
                        : normalized;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(
            String password
    ) {
        /*
         * 비밀번호는 자동 trim 하지 않습니다.
         */
        this.password =
                password;
    }
}