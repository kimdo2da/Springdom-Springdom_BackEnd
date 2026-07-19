package com.example.lightsafe.user;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public final class SecurityErrorResponseWriter {

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
        response.setContentType("application/json;charset=UTF-8");

        String safeCode = escapeJson(code);
        String safeMessage = escapeJson(message);

        String json = """
                {
                  "success": false,
                  "data": null,
                  "message": null,
                  "error": {
                    "code": "%s",
                    "message": "%s"
                  }
                }
                """.formatted(
                safeCode,
                safeMessage
        );

        response.getWriter().write(json);
        response.getWriter().flush();
    }

    private static String escapeJson(
            String value
    ) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}