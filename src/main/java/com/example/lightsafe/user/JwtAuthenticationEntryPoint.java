package com.example.lightsafe.user;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint
        implements AuthenticationEntryPoint {

    public static final String JWT_ERROR_MESSAGE =
            "JWT_ERROR_MESSAGE";

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {

        Object messageAttribute =
                request.getAttribute(
                        JWT_ERROR_MESSAGE
                );

        String message;

        if (messageAttribute instanceof String value
                && !value.isBlank()) {

            message = value;

        } else {
            message =
                    "로그인이 필요합니다.";
        }

        SecurityErrorResponseWriter.write(
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                "UNAUTHORIZED",
                message
        );
    }
}