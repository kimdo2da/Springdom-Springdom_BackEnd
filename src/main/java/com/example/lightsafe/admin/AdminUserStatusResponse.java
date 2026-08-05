package com.example.lightsafe.admin;

public record AdminUserStatusResponse(
        Long userId,
        String username,
        String nickname,
        String role,
        boolean isBlacklisted,
        int falseReportCount,
        boolean requiresRelogin
) {
}