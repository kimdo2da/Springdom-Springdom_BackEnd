package com.example.lightsafe.user;

import com.example.lightsafe.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public final class SecurityErrorResponseWriter {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper();

    private SecurityErrorResponseWriter() {
    }

    public static void write(
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws IOException {

        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(
                "application/json;charset=UTF-8"
        );

        ApiResponse<Void> body =
                ApiResponse.fail(
                        code,
                        message
                );

        OBJECT_MAPPER.writeValue(
                response.getWriter(),
                body
        );
    }
}